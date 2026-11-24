package software.ulpgc.chess.frontend.adapters.http;

import software.ulpgc.chess.contract.PresenceView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.contract.WireCodec;
import software.ulpgc.chess.frontend.ports.ChessService;
import software.ulpgc.chess.frontend.ports.ServiceException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Java's standard HTTP adapter. Requests and decoding complete asynchronously. */
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

    @Override public CompletableFuture<List<RoomView>> rooms() {
        return request("GET", "/rooms", null, null).thenApply(WireCodec::decodeRooms);
    }
    @Override public CompletableFuture<RoomView> room(String id) {
        return request("GET", path(id), null, null).thenApply(WireCodec::decodeRoom);
    }
    @Override public CompletableFuture<PresenceView> presence(String id, String token) {
        return request("POST", path(id) + "/presence", Map.of(), token).thenApply(WireCodec::decodePresence);
    }
    @Override public CompletableFuture<SessionView> create(String player) {
        return request("POST", "/rooms", Map.of("player", player), null).thenApply(WireCodec::decodeSession);
    }
    @Override public CompletableFuture<SessionView> join(String id, String player) {
        return request("POST", path(id) + "/join", Map.of("player", player), null).thenApply(WireCodec::decodeSession);
    }
    @Override public CompletableFuture<RoomView> move(String id, String token, String from, String to, String promotion, long version) {
        return request("POST", path(id) + "/moves",
                Map.of("from", from, "to", to, "promotion", promotion, "version", Long.toString(version)), token)
                .thenApply(WireCodec::decodeRoom);
    }
    @Override public CompletableFuture<RoomView> resign(String id, String token, long version) {
        return request("POST", path(id) + "/resign", Map.of("version", Long.toString(version)), token)
                .thenApply(WireCodec::decodeRoom);
    }

    private String path(String id) {
        return "/rooms/" + URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private CompletableFuture<String> request(String method, String path, Map<String, String> fields, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(8)).header("Accept", "text/plain;charset=UTF-8");
        if (token != null) request.header("X-Player-Token", token);
        if (method.equals("GET")) request.GET();
        else request.header("Content-Type", "text/plain;charset=UTF-8").POST(
                HttpRequest.BodyPublishers.ofString(WireCodec.encodeFields(fields), StandardCharsets.UTF_8));
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, failure) -> {
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                        throw new ServiceException("No se puede conectar con el servidor. Comprueba que está iniciado y vuelve a intentarlo.", cause);
                    }
                    return body(response);
                });
    }

    private String body(HttpResponse<String> response) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
        String message;
        try { message = WireCodec.decodeFields(response.body()).getOrDefault("error", "El servidor rechazó la petición."); }
        catch (RuntimeException ignored) { message = "Respuesta no válida del servidor."; }
        throw new ServiceException(message + " (HTTP " + response.statusCode() + ")");
    }
}
