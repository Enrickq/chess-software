package software.ulpgc.chess.frontend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.chess.backend.BackendFactory;
import software.ulpgc.chess.backend.adapters.FileRoomRepository;
import software.ulpgc.chess.backend.adapters.HttpChessServer;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.contract.WireCodec;
import software.ulpgc.chess.frontend.adapters.http.HttpChessService;
import software.ulpgc.chess.frontend.application.ChessController;
import software.ulpgc.chess.frontend.ports.ServiceException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class HttpChessServiceTest {
    private HttpServer server;
    private HttpChessService service;
    private final RoomView room = new RoomView("room-one", "Ana", "Luis", "ACTIVE",
            ChessController.INITIAL_FEN, 4, "2026-10-17T10:00:00Z", List.of());

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service = new HttpChessService(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void createTransmitsAccentedNameAndReadsSession() {
        AtomicReference<Map<String, String>> fields = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        server.createContext("/rooms", exchange -> {
            method.set(exchange.getRequestMethod());
            fields.set(WireCodec.decodeFields(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            reply(exchange, 201, WireCodec.encodeSession(new SessionView(room, "secret", "White")));
        });
        server.start();
        SessionView session = await(service.create("María"));
        assertEquals("POST", method.get());
        assertEquals("María", fields.get().get("player"));
        assertEquals("White", session.color());
        assertEquals("secret", session.token());
    }

    @Test void moveTransmitsCredentialInHeaderAndVersionInBody() {
        AtomicReference<Map<String, String>> fields = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/rooms/room-one/moves", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Player-Token"));
            fields.set(WireCodec.decodeFields(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            reply(exchange, 200, WireCodec.encodeRoom(room));
        });
        server.start();
        RoomView result = await(service.move("room-one", "private", "e7", "e8", "n", 3));
        assertEquals("private", token.get());
        assertEquals(Map.of("from", "e7", "to", "e8", "promotion", "n", "version", "3"), fields.get());
        assertEquals(4, result.version());
    }

    @Test void serverErrorsBecomeReadablePortErrors() {
        server.createContext("/rooms/missing", exchange -> reply(exchange, 404, WireCodec.encodeFields(Map.of("error", "La sala no existe."))));
        server.start();
        ServiceException error = assertThrows(ServiceException.class, () -> await(service.room("missing")));
        assertTrue(error.getMessage().contains("La sala no existe"));
        assertTrue(error.getMessage().contains("404"));
    }

    @Test void refusesInvalidServerSchemesAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class, () -> new HttpChessService(URI.create("file:///tmp/rooms")));
        assertThrows(IllegalArgumentException.class, () -> new HttpChessService(URI.create("http://name:password@localhost:8080")));
    }

    @Test void returnsPendingFutureWhileServerHoldsResponse() throws InterruptedException {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/rooms", exchange -> {
            received.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    reply(exchange, 504, WireCodec.encodeFields(Map.of("error", "Test response was not released.")));
                    return;
                }
                reply(exchange, 200, WireCodec.encodeRooms(List.of(room)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        try {
            CompletableFuture<List<RoomView>> pending = assertTimeoutPreemptively(Duration.ofSeconds(3), service::rooms);
            assertTrue(received.await(5, TimeUnit.SECONDS), "The server should receive the request.");
            assertFalse(pending.isDone(), "The caller has returned while the response is still held.");
            release.countDown();
            assertEquals(List.of(room), await(pending));
        } finally {
            release.countDown();
        }
    }

    @Test void controllersPlayAgainstRealBackendAndKeepStateOnDisconnect(@TempDir Path directory) throws IOException {
        try (HttpClient realClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()) {
            ChessController white;
            RoomView before;
            try (var backend = new HttpChessServer("127.0.0.1", 0,
                    BackendFactory.service(new FileRoomRepository(directory)))) {
                backend.start();
                URI base = URI.create("http://127.0.0.1:" + backend.port());
                white = new ChessController(new HttpChessService(base, realClient));
                var black = new ChessController(new HttpChessService(base, realClient));
                await(white.create("María"));
                String id = white.view().room().id();
                assertFalse(white.view().canPlay());
                await(black.join(id, "Iván"));
                await(white.refresh());
                await(white.move("e2", "e4", ""));
                await(black.refresh());
                await(black.move("e7", "e5", ""));
                await(white.refresh());
                before = white.view().room();
                assertEquals(2, before.moves().size());
                assertEquals(3, before.version());
                assertEquals("María", before.moves().get(0).player());
                assertEquals("Iván", before.moves().get(1).player());
                assertEquals(before, black.view().room());
                assertEquals("White", white.view().color());
                assertEquals("Black", black.view().color());
            }
            ServiceException error = assertThrows(ServiceException.class, () -> await(white.refresh()));
            assertTrue(error.getMessage().contains("No se puede conectar"));
            assertSame(before, white.view().room());
            assertTrue(white.view().participant());
        }
    }

    private static <T> T await(CompletableFuture<T> pending) {
        try { return pending.orTimeout(5, TimeUnit.SECONDS).join(); }
        catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError("The asynchronous operation failed.", failure.getCause());
        }
    }

    private void reply(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
