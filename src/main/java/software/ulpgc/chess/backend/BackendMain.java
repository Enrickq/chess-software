package software.ulpgc.chess.backend;

import software.ulpgc.chess.backend.adapters.FileRoomRepository;
import software.ulpgc.chess.backend.adapters.HttpChessServer;
import software.ulpgc.chess.backend.application.ChessService;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/** Composition root: this is the only place where the backend chooses its adapters. */
public final class BackendMain {
    private BackendMain() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("BackendMain [--host 127.0.0.1] [--port 8080] [--data data]");
            return;
        }
        Map<String, String> options = options(args);
        String host = options.getOrDefault("--host", "127.0.0.1");
        int port = Integer.parseInt(options.getOrDefault("--port", "8080"));
        if (port < 1 || port > 65535) throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535");
        Path data = Path.of(options.getOrDefault("--data", "data"));
        ChessService service = BackendFactory.service(new FileRoomRepository(data));
        HttpChessServer server = new HttpChessServer(host, port, service);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "chess-shutdown"));
        server.start();
        System.out.println("Chess backend disponible en http://" + host + ":" + server.port());
        System.out.println("Partidas guardadas en " + data.toAbsolutePath().normalize());
        new CountDownLatch(1).await();
    }

    private static Map<String, String> options(String[] args) {
        if (args.length % 2 != 0) throw new IllegalArgumentException("Cada opción debe tener un valor; usa --help");
        Map<String, String> result = new HashMap<>();
        Set<String> supported = Set.of("--host", "--port", "--data");
        for (int i = 0; i < args.length; i += 2) {
            if (!supported.contains(args[i]) || result.putIfAbsent(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Opción desconocida o repetida: " + args[i]);
        }
        return result;
    }
}
