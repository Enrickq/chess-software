package software.ulpgc.chess.frontend.adapters.http;

import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.contract.WireCodec;
import software.ulpgc.chess.frontend.ports.ChessService;
import software.ulpgc.chess.frontend.ports.ServiceException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Java's standard HTTP adapter. Synchronous by design; the UI calls it off the EDT. */
public final class HttpChessService implements ChessService {
    private final URI base;
    private final HttpClient client;

    public HttpChessService(URI base) {
        this(base, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public HttpChessService(URI base, HttpClient client) {
        if (base == null || !("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getRawUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null)
            throw new IllegalArgumentException("La dirección del servidor debe ser una URL HTTP o HTTPS válida.");
        this.base = URI.create(base.toString().replaceAll("/+$", ""));
        this.client = client;
    }

    @Override public List<RoomView> rooms() { return WireCodec.decodeRooms(request("GET", "/rooms", null, null)); }
    @Override public RoomView room(String id) { return WireCodec.decodeRoom(request("GET", path(id), null, null)); }
    @Override public SessionView create(String player) {
        return WireCodec.decodeSession(request("POST", "/rooms", Map.of("player", player), null));
    }
    @Override public SessionView join(String id, String player) {
        return WireCodec.decodeSession(request("POST", path(id) + "/join", Map.of("player", player), null));
    }
    @Override public RoomView move(String id, String token, String from, String to, String promotion, long version) {
        return WireCodec.decodeRoom(request("POST", path(id) + "/moves",
                Map.of("from", from, "to", to, "promotion", promotion, "version", Long.toString(version)), token));
    }
    @Override public RoomView resign(String id, String token, long version) {
        return WireCodec.decodeRoom(request("POST", path(id) + "/resign", Map.of("version", Long.toString(version)), token));
    }

    private String path(String id) {
        return "/rooms/" + URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String request(String method, String path, Map<String, String> fields, String token) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(8)).header("Accept", "text/plain;charset=UTF-8");
            if (token != null) request.header("X-Player-Token", token);
            if (method.equals("GET")) request.GET();
            else request.header("Content-Type", "text/plain;charset=UTF-8").POST(
                    HttpRequest.BodyPublishers.ofString(WireCodec.encodeFields(fields), StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            String message;
            try { message = WireCodec.decodeFields(response.body()).getOrDefault("error", "El servidor rechazó la petición."); }
            catch (RuntimeException ignored) { message = "Respuesta no válida del servidor."; }
            throw new ServiceException(message + " (HTTP " + response.statusCode() + ")");
        } catch (IOException e) {
            throw new ServiceException("No se puede conectar con el servidor. Comprueba que está iniciado y vuelve a intentarlo.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException("Se interrumpió la conexión con el servidor.", e);
        }
    }
}
