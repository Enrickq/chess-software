package software.ulpgc.chess.frontend.ports;

import software.ulpgc.chess.contract.PresenceView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Outbound port: interaction use cases know neither HTTP nor Swing. */
public interface ChessService {
    CompletableFuture<List<RoomView>> rooms();
    CompletableFuture<RoomView> room(String id);
    CompletableFuture<PresenceView> presence(String id, String token);
    CompletableFuture<SessionView> create(String player);
    CompletableFuture<SessionView> join(String id, String player);
    CompletableFuture<RoomView> move(String id, String token, String from, String to, String promotion, long version);
    CompletableFuture<RoomView> resign(String id, String token, long version);
}
