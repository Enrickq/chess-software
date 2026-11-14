package software.ulpgc.chess.frontend;

import software.ulpgc.chess.frontend.adapters.http.HttpChessService;
import software.ulpgc.chess.frontend.adapters.swing.ChessPanel;
import software.ulpgc.chess.frontend.application.ChessController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;

public final class FrontendMain {
    private FrontendMain() {}
    public static void main(String[] args) {
        String server = "http://127.0.0.1:8080";
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--help")) {
                System.out.println("Uso: FrontendMain [--server http://127.0.0.1:8080]");
                return;
            }
            if (args[i].equals("--server") && i + 1 < args.length) server = args[++i];
            else throw new IllegalArgumentException("Argumento desconocido o incompleto: " + args[i]);
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("El frontend necesita un escritorio gráfico. El backend y las pruebas funcionan sin pantalla.");
            return;
        }
        ChessController controller = new ChessController(new HttpChessService(URI.create(server)));
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Chess Software · Salas y partidas");
            ChessPanel panel = new ChessPanel(controller);
            window.setContentPane(panel);
            window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            window.setMinimumSize(new Dimension(1060, 720));
            window.pack();
            window.setLocationRelativeTo(null);
            window.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { panel.stopPolling(); }
            });
            window.setVisible(true);
            panel.startPolling();
        });
    }
}
