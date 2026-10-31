package software.ulpgc.chess.backend;

import org.junit.jupiter.api.Test;
import software.ulpgc.chess.backend.application.ChessException;
import software.ulpgc.chess.backend.application.ChessService;
import software.ulpgc.chess.backend.model.StoredRoom;
import software.ulpgc.chess.backend.ports.RepositoryException;
import software.ulpgc.chess.backend.ports.RoomRepository;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ChessServiceTest {
    @Test
    void playersJoinPlayInTurnAndResign() {
        ChessService service = BackendFactory.service(new MemoryRepository());
        SessionView white = service.create("  Ana  ");
        assertEquals("Ana", white.room().white());
        assertEquals("WAITING", white.room().status());
        assertEquals("White", white.color());
        assertEquals(0, white.room().version());
        SessionView black = service.join(white.room().id(), "Luis");
        assertEquals("Black", black.color());
        assertEquals("ACTIVE", black.room().status());
        assertNotEquals(white.token(), black.token());
        RoomView moved = service.move(white.room().id(), white.token(), 1, "e2", "e4", null);
        assertEquals(2, moved.version());
        assertEquals(1, moved.moves().size());
        assertEquals("Ana", moved.moves().get(0).player());
        assertEquals("e4", moved.moves().get(0).to());
        RoomView reply = service.move(moved.id(), black.token(), 2, "e7", "e5", "");
        assertEquals(3, reply.version());
        RoomView resigned = service.resign(reply.id(), black.token(), 3);
        assertEquals("WHITE_WON", resigned.status());
        assertEquals(4, resigned.version());
        assertEquals(resigned, service.list().get(0));
        assertKind(ChessException.Kind.CONFLICT,
                () -> service.move(reply.id(), white.token(), 4, "g1", "f3", null));
    }

    @Test
    void rejectsSpectatorsWrongTurnStaleVersionAndIllegalMovementWithoutChangingState() {
        ChessService service = BackendFactory.service(new MemoryRepository());
        SessionView white = service.create("Ana");
        SessionView black = service.join(white.room().id(), "Luis");
        String id = white.room().id();
        assertKind(ChessException.Kind.FORBIDDEN, () -> service.move(id, "unknown", 1, "e2", "e4", null));
        assertKind(ChessException.Kind.FORBIDDEN, () -> service.resign(id, null, 1));
        assertKind(ChessException.Kind.CONFLICT, () -> service.move(id, black.token(), 1, "e7", "e5", null));
        assertKind(ChessException.Kind.CONFLICT, () -> service.move(id, white.token(), 0, "e2", "e4", null));
        assertKind(ChessException.Kind.INVALID, () -> service.move(id, white.token(), 1, "e2", "e5", null));
        assertKind(ChessException.Kind.INVALID, () -> service.move(id, white.token(), 1, "none", "e5", null));
        assertKind(ChessException.Kind.INVALID, () -> service.move(id, white.token(), 1, "e2", "e4", "k"));
        assertEquals(black.room(), service.get(id));
    }

    @Test
    void failedStorageDoesNotPublishCreateJoinMoveOrResign() {
        MemoryRepository repository = new MemoryRepository();
        ChessService service = BackendFactory.service(repository);
        repository.fail = true;
        assertThrows(RepositoryException.class, () -> service.create("Ana"));
        assertTrue(service.list().isEmpty());
        repository.fail = false;
        SessionView white = service.create("Ana");
        repository.fail = true;
        assertThrows(RepositoryException.class, () -> service.join(white.room().id(), "Luis"));
        assertEquals(white.room(), service.get(white.room().id()));
        repository.fail = false;
        SessionView black = service.join(white.room().id(), "Luis");
        repository.fail = true;
        assertThrows(RepositoryException.class, () -> service.move(white.room().id(), white.token(), 1, "e2", "e4", null));
        assertEquals(black.room(), service.get(white.room().id()));
        assertThrows(RepositoryException.class, () -> service.resign(white.room().id(), white.token(), 1));
        assertEquals(black.room(), service.get(white.room().id()));
    }

    @Test
    void onlyOneConcurrentPlayerCanTakeTheBlackSeat() throws Exception {
        ChessService service = BackendFactory.service(new MemoryRepository());
        SessionView white = service.create("Ana");
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> tryJoin(service, white.room().id(), "Luis", start);
            Callable<Boolean> second = () -> tryJoin(service, white.room().id(), "María", start);
            var one = pool.submit(first);
            var two = pool.submit(second);
            start.countDown();
            assertNotEquals(one.get(), two.get());
            assertEquals(1, service.get(white.room().id()).version());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidNamesMissingRoomsAndDuplicateSeats() {
        ChessService service = BackendFactory.service(new MemoryRepository());
        for (String name : List.of("", " ", "a".repeat(41), "A\nB"))
            assertKind(ChessException.Kind.INVALID, () -> service.create(name));
        assertKind(ChessException.Kind.INVALID, () -> service.create(null));
        assertKind(ChessException.Kind.NOT_FOUND, () -> service.get("missing"));
        SessionView white = service.create("Ana");
        assertKind(ChessException.Kind.CONFLICT, () -> service.join(white.room().id(), "ana"));
        assertKind(ChessException.Kind.CONFLICT, () -> service.resign(white.room().id(), white.token(), 0));
        service.join(white.room().id(), "Luis");
        assertKind(ChessException.Kind.CONFLICT, () -> service.join(white.room().id(), "Pedro"));
    }

    private boolean tryJoin(ChessService service, String id, String player, CountDownLatch start) throws Exception {
        start.await();
        try { service.join(id, player); return true; }
        catch (ChessException exception) { assertEquals(ChessException.Kind.CONFLICT, exception.kind()); return false; }
    }

    private void assertKind(ChessException.Kind kind, org.junit.jupiter.api.function.Executable action) {
        assertEquals(kind, assertThrows(ChessException.class, action).kind());
    }

    static final class MemoryRepository implements RoomRepository {
        private final Map<String, StoredRoom> rooms = new LinkedHashMap<>();
        boolean fail;

        @Override public List<StoredRoom> findAll() { return List.copyOf(rooms.values()); }
        @Override public void save(StoredRoom room) {
            if (fail) throw new RepositoryException("Simulated disk failure", new IOException("Full disk"));
            rooms.put(room.view().id(), room);
        }
    }
}
