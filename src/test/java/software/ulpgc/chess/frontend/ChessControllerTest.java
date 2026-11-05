package software.ulpgc.chess.frontend;

import org.junit.jupiter.api.Test;
import software.ulpgc.chess.contract.MoveView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.frontend.application.ChessController;
import software.ulpgc.chess.frontend.ports.ChessService;
import software.ulpgc.chess.frontend.ports.ServiceException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChessControllerTest {
    private final FakeService service = new FakeService();
    private final ChessController controller = new ChessController(service);

    @Test void serviceCanBeReplacedByDoubleWithoutChangingUseCases() {
        controller.create("  Ana  ");
        assertEquals("Ana", service.player);
        assertEquals("White", controller.view().color());
        assertTrue(controller.view().participant());
        assertEquals("room-one", controller.view().room().id());
    }

    @Test void moveUsesPrivateCredentialAndLatestObservedVersion() {
        controller.create("Ana");
        service.current = room("ACTIVE", 8, List.of());
        controller.refresh();
        controller.move("e2", "e4", "");
        assertEquals("private-token", service.token);
        assertEquals(8, service.version);
        assertEquals("e2e4", service.move);
        assertEquals(9, controller.view().room().version());
        assertFalse(controller.view().toString().contains("private-token"));
    }

    @Test void spectatorCannotSubmitMoveOrResign() {
        controller.watch("room-one");
        assertFalse(controller.view().participant());
        assertThrows(IllegalStateException.class, () -> controller.move("e2", "e4", ""));
        assertThrows(IllegalStateException.class, controller::resign);
        assertNull(service.token);
    }

    @Test void replayRemainsAtChosenPositionWhenRemoteGameAdvances() {
        controller.create("Ana");
        MoveView first = new MoveView(1, "e2", "e4", "", "Ana", "after-first", "2026-10-17T10:00:00Z");
        MoveView second = new MoveView(2, "e7", "e5", "", "Luis", "after-second", "2026-10-17T10:00:01Z");
        service.current = room("ACTIVE", 2, List.of(first));
        controller.refresh();
        controller.replayAt(1);
        service.current = room("ACTIVE", 3, List.of(first, second));
        controller.refresh();
        assertEquals("after-first", controller.view().fen());
        assertFalse(controller.view().live());
        assertThrows(IllegalStateException.class, () -> controller.move("g1", "f3", ""));
        controller.live();
        assertEquals(2, controller.view().index());
        assertEquals(service.current.fen(), controller.view().fen());
    }

    @Test void initialReplayAndBoundsAreExplicit() {
        controller.watch("room-one");
        controller.replayAt(0);
        assertEquals(ChessController.INITIAL_FEN, controller.view().fen());
        assertThrows(IllegalArgumentException.class, () -> controller.replayAt(-1));
        assertThrows(IllegalArgumentException.class, () -> controller.replayAt(1));
    }

    @Test void connectionFailureDoesNotDestroyRoomOrPlayerSession() {
        controller.create("Ana");
        RoomView before = controller.view().room();
        service.disconnected = true;
        assertThrows(ServiceException.class, controller::refresh);
        assertSame(before, controller.view().room());
        assertTrue(controller.view().participant());
        service.disconnected = false;
        service.current = room("ACTIVE", 3, List.of());
        controller.refresh();
        controller.move("e2", "e4", "");
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
        controller.create("Ana");
        controller.watch("room-one");
        controller.join("room-one", "Ana");
        assertEquals("White", controller.view().color());
        assertEquals(0, service.joins);
    }

    @Test void waitingRoomCannotBePlayed() {
        controller.create("Ana");
        assertFalse(controller.view().canPlay());
        assertThrows(IllegalStateException.class, () -> controller.move("e2", "e4", ""));
    }

    private static RoomView room(String status, long version, List<MoveView> moves) {
        return new RoomView("room-one", "Ana", status.equals("WAITING") ? "" : "Luis", status,
                ChessController.INITIAL_FEN, version, "2026-10-17T10:00:00Z", moves);
    }

    private static final class FakeService implements ChessService {
        private RoomView current = ChessControllerTest.room("WAITING", 1, List.of());
        private String player;
        private String token;
        private String move;
        private long version;
        private boolean disconnected;
        private int joins;
        @Override public List<RoomView> rooms() { return List.of(current); }
        @Override public RoomView room(String id) {
            if (disconnected) throw new ServiceException("Desconectado");
            return current;
        }
        @Override public SessionView create(String player) {
            this.player = player;
            return new SessionView(current, "private-token", "White");
        }
        @Override public SessionView join(String id, String player) {
            joins++;
            this.player = player;
            return new SessionView(current, "other-token", "Black");
        }
        @Override public RoomView move(String id, String token, String from, String to, String promotion, long version) {
            this.token = token;
            this.version = version;
            this.move = from + to;
            current = ChessControllerTest.room("ACTIVE", version + 1, List.of());
            return current;
        }
        @Override public RoomView resign(String id, String token, long version) {
            this.token = token;
            return ChessControllerTest.room("BLACK_WON", version + 1, List.of());
        }
    }
}
