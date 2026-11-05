package software.ulpgc.chess.backend.adapters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.ulpgc.chess.backend.application.ChessException;
import software.ulpgc.chess.backend.ports.ChessUseCases;
import software.ulpgc.chess.backend.ports.RepositoryException;
import software.ulpgc.chess.contract.WireCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Inbound adapter. The service owns all authorization and game state transitions. */
public final class HttpChessServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 8192;
    private final HttpServer server;
    private final ChessUseCases service;
    private final ExecutorService workers;

    public HttpChessServer(String host, int port, ChessUseCases service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(host, port), 32);
        this.workers = Executors.newFixedThreadPool(4);
        server.setExecutor(workers);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    @Override
    public void close() {
        server.stop(0);
        workers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ChessException exception) {
            int status = switch (exception.kind()) {
                case INVALID -> 400;
                case FORBIDDEN -> 403;
                case NOT_FOUND -> 404;
                case CONFLICT -> 409;
            };
            error(exchange, status, exception.getMessage());
        } catch (IllegalArgumentException exception) {
            error(exchange, 400, "Petición no válida: " + exception.getMessage());
        } catch (RepositoryException exception) {
            error(exchange, 500, "No se pudo guardar la partida; comprueba el almacenamiento del servidor");
        } catch (RuntimeException exception) {
            error(exchange, 500, "Error interno del servidor");
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals("/health")) {
            if (!allowed(exchange, "GET")) return;
            send(exchange, 200, WireCodec.encodeFields(Map.of("status", "ok")));
            return;
        }
        if (path.equals("/rooms")) {
            if (!allowed(exchange, "GET", "POST")) return;
            if (method.equals("GET")) send(exchange, 200, WireCodec.encodeRooms(service.list()));
            else {
                Map<String, String> fields = fields(exchange, Set.of("player"));
                var session = service.create(fields.get("player"));
                exchange.getResponseHeaders().set("Location", "/rooms/" + session.room().id());
                send(exchange, 201, WireCodec.encodeSession(session));
            }
            return;
        }
        String[] parts = path.split("/", -1);
        if (parts.length < 3 || parts.length > 4 || !parts[1].equals("rooms") || parts[2].isEmpty()) {
            error(exchange, 404, "Ruta no encontrada");
            return;
        }
        String id = parts[2];
        if (parts.length == 3) {
            if (!allowed(exchange, "GET")) return;
            send(exchange, 200, WireCodec.encodeRoom(service.get(id)));
            return;
        }
        switch (parts[3]) {
            case "join" -> {
                if (!allowed(exchange, "POST")) return;
                Map<String, String> fields = fields(exchange, Set.of("player"));
                send(exchange, 200, WireCodec.encodeSession(service.join(id, fields.get("player"))));
            }
            case "moves" -> {
                if (!allowed(exchange, "POST")) return;
                Map<String, String> fields = fields(exchange, Set.of("from", "to", "promotion", "version"));
                send(exchange, 200, WireCodec.encodeRoom(service.move(id, credential(exchange), version(fields),
                        fields.get("from"), fields.get("to"), fields.get("promotion"))));
            }
            case "resign" -> {
                if (!allowed(exchange, "POST")) return;
                Map<String, String> fields = fields(exchange, Set.of("version"));
                send(exchange, 200, WireCodec.encodeRoom(service.resign(id, credential(exchange), version(fields))));
            }
            default -> error(exchange, 404, "Ruta no encontrada");
        }
    }

    private Map<String, String> fields(HttpExchange exchange, Set<String> allowedKeys) throws IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new IllegalArgumentException("El cuerpo supera 8192 bytes");
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Se requiere texto UTF-8");
        }
        Map<String, String> fields = WireCodec.decodeFields(text);
        if (!allowedKeys.containsAll(fields.keySet())) throw new IllegalArgumentException("Hay campos desconocidos");
        return fields;
    }

    private long version(Map<String, String> fields) {
        String value = fields.get("version");
        if (value == null || !value.matches("[0-9]+")) throw new IllegalArgumentException("Falta una versión entera no negativa");
        try { return Long.parseLong(value); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("La versión es demasiado grande"); }
    }

    private String credential(HttpExchange exchange) {
        var values = exchange.getRequestHeaders().get("X-Player-Token");
        return values == null || values.size() != 1 ? null : values.get(0);
    }

    private boolean allowed(HttpExchange exchange, String... methods) throws IOException {
        for (String method : methods) if (method.equals(exchange.getRequestMethod())) return true;
        exchange.getResponseHeaders().set("Allow", String.join(", ", methods));
        error(exchange, 405, "Método no permitido");
        return false;
    }

    private void error(HttpExchange exchange, int status, String message) throws IOException {
        send(exchange, status, WireCodec.encodeFields(Map.of("error", message)));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
