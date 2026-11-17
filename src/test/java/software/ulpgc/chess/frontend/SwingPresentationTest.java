package software.ulpgc.chess.frontend;

import org.junit.jupiter.api.Test;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.frontend.adapters.swing.BoardPanel;
import software.ulpgc.chess.frontend.adapters.swing.ChessPanel;
import software.ulpgc.chess.frontend.application.ChessController;
import software.ulpgc.chess.frontend.ports.ChessService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SwingPresentationTest {
    @Test void clickingBoardRespectsWhiteAndBlackOrientation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BoardPanel board = new BoardPanel();
            board.setSize(560, 560);
            AtomicReference<String> clicked = new AtomicReference<>();
            board.onSquare(clicked::set);
            click(board, 56, 56);
            assertEquals("a8", clicked.get());
            board.display(ChessController.INITIAL_FEN, true, null, null);
            click(board, 56, 56);
            assertEquals("h1", clicked.get());
            clicked.set(null);
            click(board, 1, 1);
            assertNull(clicked.get(), "Clicking the coordinate margin must not select a square");
        });
    }

    @Test void uiCanBeConstructedAndPaintedWithoutCallingNetwork() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ChessController controller = new ChessController(new NoNetworkService());
            ChessPanel panel = new ChessPanel(controller);
            panel.setSize(1180, 790);
            layout(panel);
            BufferedImage image = new BufferedImage(1180, 790, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try { panel.printAll(graphics); }
            finally { graphics.dispose(); }
            assertNotEquals(image.getRGB(10, 10), image.getRGB(10, 120), "Header and lobby should both be painted");
            assertEquals(Piece.WhiteKing, panel.boardComponent().pieceAt("e1"));
            assertEquals(Piece.BlackKing, panel.boardComponent().pieceAt("e8"));
            assertNull(panel.boardComponent().pieceAt("e4"));
        });
    }

    @Test void pendingPollingKeepsInterfaceResponsiveAndPublishesControlsOnEdt() throws Exception {
        CountDownLatch serviceEntered = new CountDownLatch(1);
        CountDownLatch startupReturned = new CountDownLatch(1);
        CompletableFuture<List<RoomView>> response = new CompletableFuture<>();
        CountDownLatch edtResponded = new CountDownLatch(1);
        CountDownLatch controlsEnabled = new CountDownLatch(1);
        AtomicBoolean enabledOnEdt = new AtomicBoolean();
        AtomicBoolean disabledWhileWaiting = new AtomicBoolean();
        AtomicReference<ChessPanel> panel = new AtomicReference<>();
        AtomicReference<JButton> refresh = new AtomicReference<>();
        AtomicReference<Throwable> startupFailure = new AtomicReference<>();
        ChessService service = new NoNetworkService() {
            @Override public CompletableFuture<List<RoomView>> rooms() {
                serviceEntered.countDown();
                return response;
            }
        };
        try {
            SwingUtilities.invokeLater(() -> {
                try {
                    panel.set(new ChessPanel(new ChessController(service)));
                    refresh.set(findButton(panel.get(), "Actualizar salas"));
                    assertNotNull(refresh.get());
                    refresh.get().addPropertyChangeListener("enabled", event -> {
                        if (Boolean.TRUE.equals(event.getNewValue())) {
                            enabledOnEdt.set(SwingUtilities.isEventDispatchThread());
                            controlsEnabled.countDown();
                        }
                    });
                    panel.get().startPolling();
                    panel.get().stopPolling();
                } catch (Throwable failure) {
                    startupFailure.set(failure);
                } finally { startupReturned.countDown(); }
            });
            assertTrue(startupReturned.await(5, TimeUnit.SECONDS), "Starting polling must return before the response arrives");
            assertNull(startupFailure.get());
            assertTrue(serviceEntered.await(5, TimeUnit.SECONDS), "The asynchronous request should start");
            assertFalse(response.isDone(), "Starting the request must not wait for its response");
            SwingUtilities.invokeLater(() -> {
                disabledWhileWaiting.set(!refresh.get().isEnabled());
                edtResponded.countDown();
            });
            assertTrue(edtResponded.await(5, TimeUnit.SECONDS), "The EDT must respond while the future is pending");
            assertTrue(disabledWhileWaiting.get(), "A second request must remain disabled until completion");
            assertEquals(1, controlsEnabled.getCount(), "Controls must not be enabled before the response arrives");
            response.complete(List.of());
            assertTrue(controlsEnabled.await(5, TimeUnit.SECONDS), "Completion must enable the controls");
            SwingUtilities.invokeAndWait(() -> assertTrue(refresh.get().isEnabled()));
            assertTrue(enabledOnEdt.get(), "UI updates must return to the EDT even when completion happens on the test thread");
        } finally {
            response.complete(List.of());
            if (panel.get() != null) SwingUtilities.invokeAndWait(() -> panel.get().stopPolling());
        }
    }

    @Test void opponentsTurnDisablesMovementButRefreshRestoresOwnTurnControlsOnEdt() throws Exception {
        RoomView opponentsTurn = new RoomView("room-one", "Ana", "Luis", "ACTIVE",
                ChessController.INITIAL_FEN.replace(" w ", " b "), 2, "2026-10-17T10:00:00Z", List.of());
        RoomView ownTurn = new RoomView("room-one", "Ana", "Luis", "ACTIVE",
                ChessController.INITIAL_FEN, 3, "2026-10-17T10:00:00Z", List.of());
        CompletableFuture<RoomView> response = new CompletableFuture<>();
        ChessService service = new NoNetworkService() {
            @Override public CompletableFuture<SessionView> create(String player) {
                return CompletableFuture.completedFuture(new SessionView(opponentsTurn, "private-token", "White"));
            }
            @Override public CompletableFuture<List<RoomView>> rooms() {
                return CompletableFuture.completedFuture(List.of(opponentsTurn));
            }
            @Override public CompletableFuture<RoomView> room(String id) { return response; }
        };
        ChessController controller = new ChessController(service);
        controller.create("Ana").orTimeout(5, TimeUnit.SECONDS).join();
        AtomicReference<ChessPanel> panel = new AtomicReference<>();
        AtomicReference<JButton> move = new AtomicReference<>();
        AtomicReference<JButton> resign = new AtomicReference<>();
        AtomicReference<JTextField> origin = new AtomicReference<>();
        AtomicReference<JTextField> destination = new AtomicReference<>();
        AtomicBoolean enabledOnEdt = new AtomicBoolean();
        CountDownLatch movementEnabled = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                panel.set(new ChessPanel(controller));
                move.set(findButton(panel.get(), "Mover"));
                resign.set(findButton(panel.get(), "Abandonar partida"));
                origin.set(findField(panel.get(), "Casilla de origen, por ejemplo e2"));
                destination.set(findField(panel.get(), "Casilla de destino, por ejemplo e4"));
                assertNotNull(move.get());
                assertNotNull(resign.get());
                assertNotNull(origin.get());
                assertNotNull(destination.get());
                assertFalse(move.get().isEnabled());
                assertFalse(origin.get().isEnabled());
                assertFalse(destination.get().isEnabled());
                assertTrue(resign.get().isEnabled(), "A player may resign during the opponent's turn.");
                move.get().addPropertyChangeListener("enabled", event -> {
                    if (Boolean.TRUE.equals(event.getNewValue())) {
                        enabledOnEdt.set(SwingUtilities.isEventDispatchThread());
                        movementEnabled.countDown();
                    }
                });
                JButton refresh = findButton(panel.get(), "Actualizar salas");
                assertNotNull(refresh);
                refresh.doClick(0);
                assertFalse(move.get().isEnabled());
                assertFalse(origin.get().isEnabled());
                assertFalse(destination.get().isEnabled());
                assertFalse(resign.get().isEnabled(), "While refreshing, actions remain serialized.");
            });
            assertFalse(response.isDone());
            assertEquals(1, movementEnabled.getCount());
            response.complete(ownTurn);
            assertTrue(movementEnabled.await(5, TimeUnit.SECONDS), "The new turn should enable movement after refresh.");
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(move.get().isEnabled());
                assertTrue(origin.get().isEnabled());
                assertTrue(destination.get().isEnabled());
                assertTrue(resign.get().isEnabled());
            });
            assertTrue(enabledOnEdt.get(), "Completion on the test thread must publish Swing controls on the EDT.");
        } finally {
            response.complete(ownTurn);
            if (panel.get() != null) SwingUtilities.invokeAndWait(() -> panel.get().stopPolling());
        }
    }

    private void click(BoardPanel board, int x, int y) {
        board.dispatchEvent(new MouseEvent(board, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, 1, false));
    }
    private void layout(Container container) {
        container.doLayout();
        for (Component component : container.getComponents())
            if (component instanceof Container child) layout(child);
    }
    private JButton findButton(Container container, String text) {
        for (Component component : container.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) return button;
            if (component instanceof Container child) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private JTextField findField(Container container, String tooltip) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTextField field && tooltip.equals(field.getToolTipText())) return field;
            if (component instanceof Container child) {
                JTextField found = findField(child, tooltip);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static class NoNetworkService implements ChessService {
        private AssertionError called() { return new AssertionError("The presentation must not perform network requests on construction"); }
        @Override public CompletableFuture<List<RoomView>> rooms() { throw called(); }
        @Override public CompletableFuture<RoomView> room(String id) { throw called(); }
        @Override public CompletableFuture<SessionView> create(String player) { throw called(); }
        @Override public CompletableFuture<SessionView> join(String id, String player) { throw called(); }
        @Override public CompletableFuture<RoomView> move(String id, String token, String from, String to, String promotion, long version) { throw called(); }
        @Override public CompletableFuture<RoomView> resign(String id, String token, long version) { throw called(); }
    }
}
