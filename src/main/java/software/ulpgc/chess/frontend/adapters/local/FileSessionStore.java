package software.ulpgc.chess.frontend.adapters.local;

import software.ulpgc.chess.frontend.ports.SessionStore;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** One opt-in local profile, bound to the configured server. */
public final class FileSessionStore implements SessionStore {
    private final Path file;
    private final URI server;

    public FileSessionStore(Path file, URI server) {
        this.file = file.toAbsolutePath().normalize();
        if (this.file.getFileName() == null || !this.file.getFileName().toString().endsWith(".chess-session"))
            throw new IllegalArgumentException("El perfil debe terminar en .chess-session.");
        this.server = URI.create(server.toString().replaceAll("/+$", ""));
    }

    @Override
    public Optional<PlayerSession> load() {
        if (Files.notExists(file)) return Optional.empty();
        Properties fields = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            fields.load(reader);
            if (!"1".equals(fields.getProperty("schema"))) throw new IllegalArgumentException();
            var session = new PlayerSession(fields.getProperty("room", ""), fields.getProperty("token", ""),
                    fields.getProperty("color", ""));
            validate(session);
            var sessionServer = URI.create(fields.getProperty("server", ""));
            if (!server.equals(sessionServer))
                throw new IllegalStateException("El perfil pertenece a otro servidor. Elige un archivo de sesión distinto.");
            return Optional.of(session);
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("No se pudo leer el perfil de sesión local.", e);
        }
    }

    @Override
    public void save(PlayerSession session) {
        validate(session);
        Properties fields = new Properties();
        fields.setProperty("schema", "1");
        fields.setProperty("server", server.toString());
        fields.setProperty("room", session.roomId());
        fields.setProperty("color", session.color());
        fields.setProperty("token", session.token());
        Path temporary = null;
        try {
            Path directory = Files.createDirectories(file.getParent());
            temporary = Files.getFileStore(directory).supportsFileAttributeView("posix")
                    ? Files.createTempFile(directory, ".chess-session-", ".tmp",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(directory, ".chess-session-", ".tmp");
            try (var writer = Files.newBufferedWriter(temporary)) {
                fields.store(writer, "Chess Software - perfil privado");
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Puedes jugar. El perfil no se pudo guardar para el próximo arranque.", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException ignored) { /* A temporary file may remain after a disk failure. */ }
            }
        }
    }

    private void validate(PlayerSession session) {
        if (!UUID.fromString(session.roomId()).toString().equals(session.roomId())
                || !session.token().matches("[A-Za-z0-9_-]{43}")
                || !(session.color().equals("White") || session.color().equals("Black")))
            throw new IllegalArgumentException("La sesión local no tiene un formato válido.");
    }
}
