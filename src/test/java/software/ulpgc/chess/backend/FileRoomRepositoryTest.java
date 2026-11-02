package software.ulpgc.chess.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.chess.backend.adapters.FileRoomRepository;
import software.ulpgc.chess.backend.application.ChessService;
import software.ulpgc.chess.backend.ports.RepositoryException;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileRoomRepositoryTest {
    @TempDir Path directory;

    @Test
    void restartingPreservesBoardHistoryAndAuthenticationWithoutPlainTokens() throws IOException {
        ChessService original = BackendFactory.service(new FileRoomRepository(directory));
        SessionView white = original.create("María");
        SessionView black = original.join(white.room().id(), "Iván");
        RoomView moved = original.move(white.room().id(), white.token(), 1, "e2", "e4", null);
        Path file = directory.resolve(moved.id() + ".room");
        String persisted = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(persisted.contains(white.token()));
        assertFalse(persisted.contains(black.token()));
        assertTrue(persisted.contains("whiteTokenHash"));
        ChessService restarted = BackendFactory.service(new FileRoomRepository(directory));
        assertEquals(moved, restarted.get(moved.id()));
        RoomView reply = restarted.move(moved.id(), black.token(), 2, "e7", "e5", null);
        assertEquals(2, reply.moves().size());
        assertEquals("Iván", reply.moves().get(1).player());
        assertEquals(reply, BackendFactory.service(new FileRoomRepository(directory)).get(moved.id()));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void corruptFileStopsReloadInsteadOfSilentlyLosingGame() throws IOException {
        ChessService service = BackendFactory.service(new FileRoomRepository(directory));
        SessionView created = service.create("Ana");
        Path file = directory.resolve(created.room().id() + ".room");
        Files.writeString(file, "schema=1\nroom=invalid\n", StandardCharsets.UTF_8);
        assertThrows(RepositoryException.class, () -> BackendFactory.service(new FileRoomRepository(directory)));
        assertTrue(Files.exists(file));
    }

    @Test
    void filenameTamperingIsDetected() throws IOException {
        ChessService service = BackendFactory.service(new FileRoomRepository(directory));
        SessionView created = service.create("Ana");
        Files.move(directory.resolve(created.room().id() + ".room"), directory.resolve("other.room"));
        assertThrows(RepositoryException.class, () -> BackendFactory.service(new FileRoomRepository(directory)));
    }

    @Test
    void leftoverTemporaryFileDoesNotReplaceCommittedGame() throws IOException {
        ChessService service = BackendFactory.service(new FileRoomRepository(directory));
        SessionView created = service.create("Ana");
        Files.writeString(directory.resolve(".room-interrupted.tmp"), "partial", StandardCharsets.UTF_8);
        assertEquals(created.room(), BackendFactory.service(new FileRoomRepository(directory)).get(created.room().id()));
    }

    @Test
    void fileInPlaceOfDirectoryFailsExplicitly() throws IOException {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "data");
        assertThrows(RepositoryException.class, () -> new FileRoomRepository(file));
    }
}
