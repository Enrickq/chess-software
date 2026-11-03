package software.ulpgc.chess.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.chess.backend.adapters.FileRoomRepository;
import software.ulpgc.chess.backend.adapters.HttpChessServer;
import software.ulpgc.chess.backend.application.ChessService;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.WireCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpChessServerTest {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Test
    void twoClientsAndSpectatorPlayOverHttpAndContinueAfterRestart() throws Exception {
        String whiteToken;
        String blackToken;
        String id;
        RoomView moved;
        try (HttpChessServer server = server()) {
            assertEquals(200, request(server, "GET", "/health", null, null).statusCode());
            var create = request(server, "POST", "/rooms", Map.of("player", "Ana"), null);
            assertEquals(201, create.statusCode());
            var white = WireCodec.decodeSession(create.body());
            id = white.room().id();
            whiteToken = white.token();
            assertEquals("/rooms/" + id, create.headers().firstValue("Location").orElseThrow());
            var join = request(server, "POST", "/rooms/" + id + "/join", Map.of("player", "Luis"), null);
            assertEquals(200, join.statusCode());
            blackToken = WireCodec.decodeSession(join.body()).token();
            assertEquals(409, request(server, "POST", "/rooms/" + id + "/join", Map.of("player", "Third"), null).statusCode());
            Map<String, String> move = Map.of("from", "e2", "to", "e4", "version", "1");
            assertEquals(403, request(server, "POST", "/rooms/" + id + "/moves", move, null).statusCode());
            assertEquals(403, request(server, "POST", "/rooms/" + id + "/moves", move, "bad-token").statusCode());
            assertEquals(409, request(server, "POST", "/rooms/" + id + "/moves", move, blackToken).statusCode());
            var result = request(server, "POST", "/rooms/" + id + "/moves", move, whiteToken);
            assertEquals(200, result.statusCode());
            moved = WireCodec.decodeRoom(result.body());
            assertEquals(1, moved.moves().size());
            assertEquals(409, request(server, "POST", "/rooms/" + id + "/moves", move, whiteToken).statusCode());
            var publicRoom = request(server, "GET", "/rooms/" + id, null, null);
            assertEquals(moved, WireCodec.decodeRoom(publicRoom.body()));
            var publicList = request(server, "GET", "/rooms", null, null);
            assertEquals(1, WireCodec.decodeRooms(publicList.body()).size());
            for (String body : new String[]{publicRoom.body(), publicList.body()}) {
                assertFalse(body.contains(whiteToken));
                assertFalse(body.contains(blackToken));
                assertFalse(body.contains("TokenHash"));
            }
        }
        try (HttpChessServer restarted = server()) {
            assertEquals(moved, WireCodec.decodeRoom(request(restarted, "GET", "/rooms/" + id, null, null).body()));
            var reply = request(restarted, "POST", "/rooms/" + id + "/moves",
                    Map.of("from", "e7", "to", "e5", "version", "2"), blackToken);
            assertEquals(200, reply.statusCode());
            var resigned = request(restarted, "POST", "/rooms/" + id + "/resign", Map.of("version", "3"), whiteToken);
            assertEquals(200, resigned.statusCode());
            assertEquals("BLACK_WON", WireCodec.decodeRoom(resigned.body()).status());
        }
    }

    @Test
    void malformedRequestsHaveUsefulErrorsAndCannotMutateRooms() throws Exception {
        try (HttpChessServer server = server()) {
            assertEquals(404, request(server, "GET", "/unknown", null, null).statusCode());
            var wrongMethod = request(server, "DELETE", "/rooms", null, null);
            assertEquals(405, wrongMethod.statusCode());
            assertEquals("GET, POST", wrongMethod.headers().firstValue("Allow").orElseThrow());
            assertEquals(400, request(server, "POST", "/rooms", Map.of(), null).statusCode());
            assertEquals(400, request(server, "POST", "/rooms", Map.of("player", "Ana", "admin", "true"), null).statusCode());
            assertEquals(400, request(server, "POST", "/rooms", Map.of("player", "a".repeat(9000)), null).statusCode());
            var white = WireCodec.decodeSession(request(server, "POST", "/rooms", Map.of("player", "Ana"), null).body());
            var badVersion = request(server, "POST", "/rooms/" + white.room().id() + "/resign",
                    Map.of("version", "abc"), white.token());
            assertEquals(400, badVersion.statusCode());
            assertTrue(WireCodec.decodeFields(badVersion.body()).containsKey("error"));
            assertEquals(404, request(server, "GET", "/rooms/missing", null, null).statusCode());
            assertEquals(1, WireCodec.decodeRooms(request(server, "GET", "/rooms", null, null).body()).size());
        }
    }

    @Test
    void diskFailureReturns500WithoutPublishingRoom() throws Exception {
        var repository = new ChessServiceTest.MemoryRepository();
        repository.fail = true;
        try (var server = new HttpChessServer("127.0.0.1", 0, BackendFactory.service(repository))) {
            server.start();
            assertEquals(500, request(server, "POST", "/rooms", Map.of("player", "Ana"), null).statusCode());
            assertTrue(WireCodec.decodeRooms(request(server, "GET", "/rooms", null, null).body()).isEmpty());
        }
    }

    private HttpChessServer server() throws IOException {
        var server = new HttpChessServer("127.0.0.1", 0, BackendFactory.service(new FileRoomRepository(directory)));
        server.start();
        return server;
    }

    private HttpResponse<String> request(HttpChessServer server, String method, String path,
                                         Map<String, String> body, String token) throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(5)).header("Content-Type", "text/plain; charset=UTF-8");
        if (token != null) builder.header("X-Player-Token", token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(WireCodec.encodeFields(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
