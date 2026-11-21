package software.ulpgc.chess.frontend;

import org.junit.jupiter.api.Test;
import software.ulpgc.chess.contract.MoveView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.frontend.application.ChessController;
import software.ulpgc.chess.frontend.ports.ChessService;
import software.ulpgc.chess.frontend.ports.ServiceException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ChessControllerTest {
    private final FakeService service = new FakeService();
    private final ChessController controller = new ChessController(service);

    @Test void serviceCanBeReplacedByDoubleWithoutChangingUseCases() {
        await(controller.create("  Ana  "));
        assertEquals("Ana", service.player);
        assertEquals("White", controller.view().color());
        assertTrue(controller.view().participant());
        assertEquals("room-one", controller.view().room().id());
    }

    @Test void moveUsesPrivateCredentialAndLatestObservedVersion() {
        await(controller.create("Ana"));
        service.current = room("ACTIVE", 8, List.of());
        await(controller.refresh());
        await(controller.move("e2", "e4", ""));
        assertEquals("private-token", service.token);
        assertEquals(8, service.version);
        assertEquals("e2e4", service.move);
        assertEquals(9, controller.view().room().version());
        assertFalse(controller.view().toString().contains("private-token"));
    }

    @Test void spectatorCannotSubmitMoveOrResign() {
        service.current = room("ACTIVE", 2, List.of());
        await(controller.watch("room-one"));
        assertFalse(controller.view().participant());
        assertFalse(controller.view().canPlay());
        assertFalse(controller.view().canResign());
        assertThrows(IllegalStateException.class, () -> controller.move("e2", "e4", ""));
        assertThrows(IllegalStateException.class, controller::resign);
        assertNull(service.token);
        assertEquals(0, service.moveCalls);
        assertEquals(0, service.resignCalls);
    }

    @Test void replayRemainsAtChosenPositionWhenRemoteGameAdvances() {
        await(controller.create("Ana"));
        MoveView first = new MoveView(1, "e2", "e4", "", "Ana", "after-first", "2026-10-17T10:00:00Z");
        MoveView second = new MoveView(2, "e7", "e5", "", "Luis", "after-second", "2026-10-17T10:00:01Z");
        service.current = room("ACTIVE", 2, List.of(first));
        await(controller.refresh());
        controller.replayAt(1);
        service.current = room("ACTIVE", 3, List.of(first, second));
        await(controller.refresh());
        assertEquals("after-first", controller.view().fen());
        assertFalse(controller.view().live());
        assertFalse(controller.view().canPlay());
        assertFalse(controller.view().canResign());
        assertThrows(IllegalStateException.class, () -> controller.move("g1", "f3", ""));
        assertThrows(IllegalStateException.class, controller::resign);
        assertEquals(0, service.moveCalls);
        assertEquals(0, service.resignCalls);
        controller.live();
        assertEquals(2, controller.view().index());
        assertEquals(service.current.fen(), controller.view().fen());
        assertTrue(controller.view().canPlay());
        assertTrue(controller.view().canResign());
    }

    @Test void initialReplayAndBoundsAreExplicit() {
        await(controller.watch("room-one"));
        controller.replayAt(0);
        assertEquals(ChessController.INITIAL_FEN, controller.view().fen());
        assertThrows(IllegalArgumentException.class, () -> controller.replayAt(-1));
        assertThrows(IllegalArgumentException.class, () -> controller.replayAt(1));
    }

    @Test void connectionFailureDoesNotDestroyRoomOrPlayerSession() {
        await(controller.create("Ana"));
        RoomView before = controller.view().room();
        service.disconnected = true;
        CompletionException error = assertThrows(CompletionException.class, () -> await(controller.refresh()));
        assertInstanceOf(ServiceException.class, error.getCause());
        assertSame(before, controller.view().room());
        assertTrue(controller.view().participant());
        service.disconnected = false;
        service.current = room("ACTIVE", 3, List.of());
        await(controller.refresh());
        await(controller.move("e2", "e4", ""));
        assertEquals("private-token", service.token);
    }

    @Test void blankInputFailsBeforeCallingService() {
        assertThrows(IllegalArgumentException.class, () -> controller.create(" "));
        assertThrows(IllegalArgumentException.class, () -> controller.join("", "Ana"));
        assertThrows(IllegalArgumentException.class, () -> controller.watch(" "));
        assertNull(service.player);
        assertNull(controller.view().room());
    }

    @Test void reopeningOwnedRoomRestoresPlayerRoleWithoutTakingOtherSeat() {
        await(controller.create("Ana"));
        await(controller.watch("room-one"));
        await(controller.join("room-one", "Ana"));
        assertEquals("White", controller.view().color());
        assertEquals(0, service.joins);
    }

    @Test void waitingRoomCannotBePlayed() {
        assertFalse(controller.view().canPlay());
        assertFalse(controller.view().canResign());
        assertThrows(IllegalStateException.class, () -> controller.move("e2", "e4", ""));
        assertThrows(IllegalStateException.class, controller::resign);
        await(controller.create("Ana"));
        assertFalse(controller.view().canPlay());
        assertFalse(controller.view().canResign());
        assertThrows(IllegalStateException.class, () -> controller.move("e2", "e4", ""));
        assertThrows(IllegalStateException.class, controller::resign);
        assertEquals(0, service.moveCalls);
        assertEquals(0, service.resignCalls);
    }

    @Test void eachColorMovesOnlyOnItsTurnAndRefreshFollowsTheRemoteTurn() {
        await(controller.create("Ana"));
        service.current = room("ACTIVE", 2, List.of());
        ChessController black = new ChessController(service);
        await(black.join("room-one", "Luis"));
        await(controller.refresh());
        assertTrue(controller.view().canPlay());
        assertFalse(black.view().canPlay());
        assertTrue(black.view().canResign());
        assertThrows(IllegalStateException.class, () -> black.move("e7", "e5", ""));
        assertEquals(0, service.moveCalls, "An out-of-turn move must not reach the port.");

        await(controller.move("e2", "e4", ""));
        assertEquals(1, service.moveCalls);
        assertFalse(controller.view().canPlay(), "White must wait after its accepted move.");
        assertTrue(controller.view().canResign());
        assertThrows(IllegalStateException.class, () -> controller.move("g1", "f3", ""));
        assertEquals(1, service.moveCalls);
        assertFalse(black.view().canPlay(), "Black still has the earlier snapshot until refresh completes.");
        await(black.refresh());
        assertTrue(black.view().canPlay());

        await(black.move("e7", "e5", ""));
        assertEquals(2, service.moveCalls);
        assertEquals("other-token", service.token);
        assertFalse(black.view().canPlay(), "Black must wait after its accepted move.");
        assertTrue(black.view().canResign());
        assertThrows(IllegalStateException.class, () -> black.move("b8", "c6", ""));
        assertEquals(2, service.moveCalls);
        assertFalse(controller.view().canPlay());
        await(controller.refresh());
        assertTrue(controller.view().canPlay());
    }

    @Test void eitherPlayerCanResignOutsideTheirTurnAndFinishedRoomsRejectFurtherActions() {
        for (String color : List.of("White", "Black")) {
            FakeService remote = new FakeService();
            remote.current = room("ACTIVE", 4, List.of(), color.equals("White") ? "b" : "w");
            ChessController player = new ChessController(remote);
            if (color.equals("White")) await(player.create("Ana"));
            else await(player.join("room-one", "Luis"));
            assertEquals(color, player.view().color());
            assertFalse(player.view().canPlay());
            assertTrue(player.view().canResign());

            await(player.resign());
            assertEquals(1, remote.resignCalls);
            assertEquals(0, remote.moveCalls);
            assertEquals(4, remote.version);
            assertEquals(color.equals("White") ? "BLACK_WON" : "WHITE_WON", player.view().room().status());
            assertFalse(player.view().canPlay());
            assertFalse(player.view().canResign());
            assertThrows(IllegalStateException.class, () -> player.move("e2", "e4", ""));
            assertThrows(IllegalStateException.class, player::resign);
            assertEquals(1, remote.resignCalls, "A finished room must reject a repeated resignation locally.");
            assertEquals(0, remote.moveCalls);
        }
    }

    @Test void remoteStateIsPublishedOnlyAfterPendingResponseCompletes() {
        await(controller.create("Ana"));
        RoomView before = controller.view().room();
        service.pendingRoom = new CompletableFuture<RoomView>().orTimeout(5, TimeUnit.SECONDS);
        CompletableFuture<Void> pendingRefresh = controller.refresh();
        assertFalse(pendingRefresh.isDone());
        assertSame(before, controller.view().room());
        assertFalse(controller.view().canPlay());

        RoomView updated = room("ACTIVE", 2, List.of());
        service.pendingRoom.complete(updated);
        await(pendingRefresh);
        assertSame(updated, controller.view().room());
        assertTrue(controller.view().canPlay());
    }

    private void await(CompletableFuture<Void> operation) {
        operation.orTimeout(5, TimeUnit.SECONDS).join();
    }

    private static RoomView room(String status, long version, List<MoveView> moves) {
        return room(status, version, moves, "w");
    }

    private static RoomView room(String status, long version, List<MoveView> moves, String turn) {
        return new RoomView("room-one", "Ana", status.equals("WAITING") ? "" : "Luis", status,
                ChessController.INITIAL_FEN.replace(" w ", " " + turn + " "), version, "2026-10-17T10:00:00Z", moves);
    }

    private static final class FakeService implements ChessService {
        private RoomView current = ChessControllerTest.room("WAITING", 1, List.of());
        private String player;
        private String token;
        private String move;
        private long version;
        private boolean disconnected;
        private CompletableFuture<RoomView> pendingRoom;
        private int joins;
        private int moveCalls;
        private int resignCalls;
        @Override public CompletableFuture<List<RoomView>> rooms() { return CompletableFuture.completedFuture(List.of(current)); }
        @Override public CompletableFuture<RoomView> room(String id) {
            if (disconnected) return CompletableFuture.failedFuture(new ServiceException("Desconectado"));
            return pendingRoom == null ? CompletableFuture.completedFuture(current) : pendingRoom;
        }
        @Override public CompletableFuture<SessionView> create(String player) {
            this.player = player;
            return CompletableFuture.completedFuture(new SessionView(current, "private-token", "White"));
        }
        @Override public CompletableFuture<SessionView> join(String id, String player) {
            joins++;
            this.player = player;
            return CompletableFuture.completedFuture(new SessionView(current, "other-token", "Black"));
        }
        @Override public CompletableFuture<RoomView> move(String id, String token, String from, String to, String promotion, long version) {
            moveCalls++;
            this.token = token;
            this.version = version;
            this.move = from + to;
            String nextTurn = current.fen().split(" ")[1].equals("w") ? "b" : "w";
            current = ChessControllerTest.room("ACTIVE", version + 1, List.of(), nextTurn);
            return CompletableFuture.completedFuture(current);
        }
        @Override public CompletableFuture<RoomView> resign(String id, String token, long version) {
            resignCalls++;
            this.token = token;
            this.version = version;
            current = ChessControllerTest.room(token.equals("private-token") ? "BLACK_WON" : "WHITE_WON", version + 1, List.of(), current.fen().split(" ")[1]);
            return CompletableFuture.completedFuture(current);
        }
    }
}
