package software.ulpgc.chess.verification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.chess.backend.BackendFactory;
import software.ulpgc.chess.backend.adapters.FileRoomRepository;
import software.ulpgc.chess.backend.adapters.HttpChessServer;
import software.ulpgc.chess.frontend.adapters.http.HttpChessService;
import software.ulpgc.chess.frontend.adapters.swing.ChessPanel;
import software.ulpgc.chess.frontend.application.ChessController;
import software.ulpgc.chess.frontend.ports.ServiceException;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP, real disk adapter and the same controllers/panel used by the desktop frontend. */
class EndToEndTest {
    @TempDir Path data;

    @Test void twoFrontendsReplayAndRecoverPersistedGame() throws Exception {
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            String id;
            String finalFen;
            try (var server = server()) {
                ChessController white = client(server, http), black = client(server, http), spectator = client(server, http);
                white.create("Ana");
                id = white.view().room().id();
                white.listRooms();
                capture(white, "01-sala.png", "Sala creada por Ana. Código listo para compartir con el segundo jugador.");
                black.join(id, "Luis");
                white.refresh();
                assertEquals("ACTIVE", white.view().room().status());
                long versionBeforeInvalidMove = white.view().room().version();
                assertThrows(ServiceException.class, () -> white.move("e2", "e5", ""));
                white.refresh();
                assertEquals(versionBeforeInvalidMove, white.view().room().version());
                assertEquals(0, white.view().room().moves().size());
                white.move("e2", "e4", "");
                black.refresh(); black.move("e7", "e5", "");
                white.refresh(); white.move("g1", "f3", "");
                black.refresh(); black.move("b8", "c6", "");
                white.refresh(); white.listRooms();
                finalFen = white.view().room().fen();
                assertEquals(4, white.view().room().moves().size());
                capture(white, "02-tablero.png", "Dos jugadores conectados por HTTP. Cuatro movimientos registrados y guardados en disco.");
                spectator.watch(id); spectator.listRooms(); spectator.replayAt(2);
                assertFalse(spectator.view().live());
                assertEquals(spectator.view().room().moves().get(1).fen(), spectator.view().fen());
                assertNotEquals(finalFen, spectator.view().fen());
                assertThrows(IllegalStateException.class, () -> spectator.move("a2", "a3", ""));
                capture(spectator, "03-historico.png", "Reproducción: posición tras e2–e4 y e7–e5. El historial conserva las cuatro jugadas.");
                spectator.live();
                assertEquals(finalFen, spectator.view().fen());
            }
            ChessController observer;
            try (var restarted = server()) {
                observer = client(restarted, http);
                observer.watch(id);
                assertEquals(finalFen, observer.view().room().fen());
                assertEquals(4, observer.view().room().moves().size());
                observer.replayAt(0);
                assertEquals(ChessController.INITIAL_FEN, observer.view().fen());
            }
            assertThrows(ServiceException.class, observer::refresh);
            assertEquals(4, observer.view().room().moves().size(), "La desconexión no borra la vista cargada");
        }
    }

    private HttpChessServer server() throws Exception {
        var result = new HttpChessServer("127.0.0.1",0, BackendFactory.service(new FileRoomRepository(data)));
        result.start(); return result;
    }
    private ChessController client(HttpChessServer server, HttpClient http) {
        return new ChessController(new HttpChessService(URI.create("http://127.0.0.1:" + server.port()), http));
    }
    private void capture(ChessController controller, String name, String message) throws Exception {
        String output = System.getProperty("chess.evidence");
        if (output == null || output.isBlank()) return;
        Path directory = Path.of(output);
        Files.createDirectories(directory);
        BufferedImage image = new BufferedImage(1280, 820, BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            ChessPanel panel = new ChessPanel(controller);
            panel.loadView("HTTP local verificado", message);
            panel.setSize(image.getWidth(), image.getHeight());
            layout(panel);
            var graphics = image.createGraphics();
            try { panel.printAll(graphics); } finally { graphics.dispose(); }
            panel.stopPolling();
        });
        assertTrue(ImageIO.write(image, "png", directory.resolve(name).toFile()));
    }
    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container);
    }
}
