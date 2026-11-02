package software.ulpgc.chess.backend.adapters;

import software.ulpgc.chess.adapters.FenPositionDeserializer;
import software.ulpgc.chess.adapters.FenPositionSerializer;
import software.ulpgc.chess.backend.model.StoredRoom;
import software.ulpgc.chess.backend.ports.RepositoryException;
import software.ulpgc.chess.backend.ports.RoomRepository;
import software.ulpgc.chess.contract.MoveView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.WireCodec;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.domain.model.Color;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.domain.model.Position;
import software.ulpgc.chess.domain.service.MoveRules;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** One atomic UTF-8 properties file per room. Run one server per data directory. */
public final class FileRoomRepository implements RoomRepository {
    private static final long MAX_FILE_BYTES = 16 * 1024 * 1024;
    private final Path directory;
    private final FenPositionDeserializer deserializer = new FenPositionDeserializer();

    public FileRoomRepository(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.directory);
        } catch (IOException exception) {
            throw new RepositoryException("No se pudo preparar el directorio de partidas", exception);
        }
    }

    @Override
    public synchronized List<StoredRoom> findAll() {
        try (var files = Files.list(directory)) {
            List<StoredRoom> result = new ArrayList<>();
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".room")).sorted().toList()) {
                if (Files.size(file) > MAX_FILE_BYTES) throw new IOException("Archivo de partida demasiado grande");
                Map<String, String> fields = WireCodec.decodeFields(Files.readString(file, StandardCharsets.UTF_8));
                if (!"1".equals(fields.get("schema"))) throw new IllegalArgumentException("Versión de persistencia desconocida");
                StoredRoom room = new StoredRoom(WireCodec.decodeRoom(required(fields, "room")),
                        required(fields, "whiteTokenHash"), required(fields, "blackTokenHash"));
                validate(room);
                if (!file.getFileName().toString().equals(room.view().id() + ".room"))
                    throw new IllegalArgumentException("El archivo y el identificador de sala no coinciden");
                result.add(room);
            }
            return List.copyOf(result);
        } catch (IOException | RuntimeException exception) {
            throw new RepositoryException("No se pudieron leer las partidas; revisa los archivos sin borrarlos", exception);
        }
    }

    @Override
    public synchronized void save(StoredRoom room) {
        validate(room);
        Path temporary = null;
        try {
            byte[] bytes = WireCodec.encodeFields(Map.of("schema", "1",
                    "room", WireCodec.encodeRoom(room.view()), "whiteTokenHash", room.whiteTokenHash(),
                    "blackTokenHash", room.blackTokenHash())).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_FILE_BYTES) throw new IOException("La partida supera el tamaño permitido");
            temporary = Files.createTempFile(directory, ".room-", ".tmp");
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            // An unsupported atomic replacement fails explicitly; no partial file is published.
            Files.move(temporary, directory.resolve(room.view().id() + ".room"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new RepositoryException("No se pudo guardar la partida; el cambio no se ha publicado", exception);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* A harmless temporary file may remain after a disk failure. */ }
            }
        }
    }

    private void validate(StoredRoom stored) {
        RoomView room = stored.view();
        if (!UUID.fromString(room.id()).toString().equals(room.id())) throw new IllegalArgumentException("ID de sala no válido");
        validateName(room.white());
        Instant.parse(room.createdAt());
        if (!Set.of("WAITING", "ACTIVE", "WHITE_WON", "BLACK_WON").contains(room.status()))
            throw new IllegalArgumentException("Estado de partida no válido");
        if (!stored.whiteTokenHash().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Credencial dañada");
        boolean waiting = room.status().equals("WAITING");
        if (waiting) {
            if (!room.black().isEmpty() || !stored.blackTokenHash().isEmpty() || room.version() != 0 || !room.moves().isEmpty())
                throw new IllegalArgumentException("Sala pendiente incoherente");
        } else {
            validateName(room.black());
            if (!stored.blackTokenHash().matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Credencial dañada");
            long expected = (long) room.moves().size() + (room.status().equals("ACTIVE") ? 1 : 2);
            if (room.version() != expected) throw new IllegalArgumentException("Versión de partida incoherente");
        }
        deserializer.deserialize(room.fen());
        Position replay = Position.initial();
        MoveRules rules = new MoveRules();
        FenPositionSerializer serializer = new FenPositionSerializer();
        for (int i = 0; i < room.moves().size(); i++) {
            MoveView move = room.moves().get(i);
            if (move.number() != i + 1) throw new IllegalArgumentException("Historial desordenado");
            Square.of(move.from());
            Square.of(move.to());
            if (!move.promotion().matches("[qrbn]?")) throw new IllegalArgumentException("Promoción persistida no válida");
            if (!move.player().equals(i % 2 == 0 ? room.white() : room.black()))
                throw new IllegalArgumentException("Jugador de historial incoherente");
            Instant.parse(move.at());
            deserializer.deserialize(move.fen());
            Piece promotion = move.promotion().isEmpty() ? null : Piece.fromSymbol(
                    replay.state().turn() == Color.White
                            ? Character.toUpperCase(move.promotion().charAt(0)) : move.promotion().charAt(0));
            replay = rules.move(replay, Square.of(move.from()), Square.of(move.to()), promotion);
            if (!serializer.serialize(replay).equals(move.fen()))
                throw new IllegalArgumentException("La posición del historial no corresponde al movimiento");
        }
        if (!room.fen().equals(serializer.serialize(replay)))
            throw new IllegalArgumentException("La posición no coincide con el último movimiento");
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.length() > 40 || name.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Nombre persistido no válido");
    }

    private String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) throw new IllegalArgumentException("Campo persistido ausente: " + key);
        return value;
    }
}
