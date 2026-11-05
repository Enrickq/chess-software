package software.ulpgc.chess.backend.application;

import software.ulpgc.chess.backend.model.StoredRoom;
import software.ulpgc.chess.backend.ports.RoomRepository;
import software.ulpgc.chess.backend.ports.ChessUseCases;
import software.ulpgc.chess.contract.MoveView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.domain.model.Color;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.domain.model.Position;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.domain.service.MoveRules;
import software.ulpgc.chess.ports.PositionSerializer;
import software.ulpgc.chess.ports.PositionDeserializer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static software.ulpgc.chess.backend.application.ChessException.Kind.*;

/** Serializes commands; persistence succeeds before a new version becomes visible. */
public final class ChessService implements ChessUseCases {
    private final RoomRepository repository;
    private final MoveRules rules;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, StoredRoom> rooms = new LinkedHashMap<>();
    private final PositionSerializer serializer;
    private final PositionDeserializer deserializer;

    public ChessService(RoomRepository repository, PositionSerializer serializer, PositionDeserializer deserializer) {
        this(repository, serializer, deserializer, new MoveRules(), Clock.systemUTC());
    }

    public ChessService(RoomRepository repository, PositionSerializer serializer, PositionDeserializer deserializer,
                        MoveRules rules, Clock clock) {
        this.repository = repository;
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.rules = rules;
        this.clock = clock;
        for (StoredRoom room : repository.findAll()) {
            if (rooms.putIfAbsent(room.view().id(), room) != null)
                throw new IllegalArgumentException("Identificador de sala duplicado");
        }
    }

    @Override
    public synchronized List<RoomView> list() {
        return rooms.values().stream().map(StoredRoom::view)
                .sorted(Comparator.comparing(RoomView::createdAt).thenComparing(RoomView::id)).toList();
    }

    @Override
    public synchronized RoomView get(String id) {
        return room(id).view();
    }

    @Override
    public synchronized SessionView create(String player) {
        String name = name(player);
        String token = token();
        RoomView view = new RoomView(UUID.randomUUID().toString(), name, "", "WAITING",
                serializer.serialize(Position.initial()), 0, now(), List.of());
        persist(new StoredRoom(view, hash(token), ""));
        return new SessionView(view, token, Color.White.name());
    }

    @Override
    public synchronized SessionView join(String id, String player) {
        String name = name(player);
        StoredRoom stored = room(id);
        RoomView current = stored.view();
        if (!current.status().equals("WAITING")) throw failure(CONFLICT, "La sala ya tiene dos jugadores");
        if (current.white().equalsIgnoreCase(name))
            throw failure(CONFLICT, "Usa un nombre distinto al del otro jugador");
        String token = token();
        RoomView next = view(current, name, "ACTIVE", current.fen(), current.moves());
        persist(new StoredRoom(next, stored.whiteTokenHash(), hash(token)));
        return new SessionView(next, token, Color.Black.name());
    }

    @Override
    public synchronized RoomView move(String id, String token, long version,
                                      String from, String to, String promotion) {
        StoredRoom stored = room(id);
        Color playerColor = authenticate(stored, token);
        RoomView current = stored.view();
        expectVersion(current, version);
        if (!current.status().equals("ACTIVE")) throw failure(CONFLICT, "La partida no está activa");
        Position position = deserializer.deserialize(current.fen());
        if (position.state().turn() != playerColor) throw failure(CONFLICT, "No es tu turno");
        Position nextPosition;
        Square origin;
        Square destination;
        String selectedPromotion = promotion == null ? "" : promotion.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            origin = Square.of(from);
            destination = Square.of(to);
            Piece promoted = null;
            if (!selectedPromotion.isEmpty()) {
                if (!selectedPromotion.matches("[qrbn]")) throw new IllegalArgumentException("Promoción no válida");
                char symbol = selectedPromotion.charAt(0);
                promoted = Piece.fromSymbol(playerColor == Color.White ? Character.toUpperCase(symbol) : symbol);
            }
            nextPosition = rules.move(position, origin, destination, promoted);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw failure(INVALID, exception.getMessage() == null ? "Movimiento no válido" : exception.getMessage());
        }
        String fen = serializer.serialize(nextPosition);
        List<MoveView> moves = new ArrayList<>(current.moves());
        moves.add(new MoveView(moves.size() + 1, origin.toString(), destination.toString(),
                selectedPromotion, playerColor == Color.White ? current.white() : current.black(), fen, now()));
        RoomView next = view(current, current.black(), current.status(), fen, List.copyOf(moves));
        persist(new StoredRoom(next, stored.whiteTokenHash(), stored.blackTokenHash()));
        return next;
    }

    @Override
    public synchronized RoomView resign(String id, String token, long version) {
        StoredRoom stored = room(id);
        Color playerColor = authenticate(stored, token);
        RoomView current = stored.view();
        expectVersion(current, version);
        if (!current.status().equals("ACTIVE")) throw failure(CONFLICT, "La partida no está activa");
        String status = playerColor == Color.White ? "BLACK_WON" : "WHITE_WON";
        RoomView next = view(current, current.black(), status, current.fen(), current.moves());
        persist(new StoredRoom(next, stored.whiteTokenHash(), stored.blackTokenHash()));
        return next;
    }

    private StoredRoom room(String id) {
        StoredRoom stored = rooms.get(id);
        if (stored == null) throw failure(NOT_FOUND, "La sala no existe");
        return stored;
    }

    private void persist(StoredRoom stored) {
        repository.save(stored);
        rooms.put(stored.view().id(), stored);
    }

    private RoomView view(RoomView old, String black, String status, String fen, List<MoveView> moves) {
        return new RoomView(old.id(), old.white(), black, status, fen,
                Math.addExact(old.version(), 1), old.createdAt(), List.copyOf(moves));
    }

    private Color authenticate(StoredRoom stored, String token) {
        if (token == null || token.length() > 256 || token.isBlank())
            throw failure(FORBIDDEN, "Credencial de jugador no válida");
        String supplied = hash(token);
        if (sameHash(supplied, stored.whiteTokenHash())) return Color.White;
        if (!stored.blackTokenHash().isEmpty() && sameHash(supplied, stored.blackTokenHash())) return Color.Black;
        throw failure(FORBIDDEN, "Credencial de jugador no válida");
    }

    private boolean sameHash(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private void expectVersion(RoomView current, long version) {
        if (version < 0) throw failure(INVALID, "La versión debe ser un entero no negativo");
        if (current.version() != version) throw failure(CONFLICT, "La partida ha cambiado: actualiza antes de continuar");
    }

    private String name(String value) {
        if (value == null) throw failure(INVALID, "El nombre es obligatorio");
        String name = value.strip();
        if (name.isEmpty() || name.length() > 40 || name.codePoints().anyMatch(Character::isISOControl))
            throw failure(INVALID, "El nombre debe tener entre 1 y 40 caracteres sin controles");
        return name;
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("La plataforma Java no proporciona SHA-256", impossible);
        }
    }

    private String now() { return Instant.now(clock).toString(); }

    private ChessException failure(ChessException.Kind kind, String message) { return new ChessException(kind, message); }
}
