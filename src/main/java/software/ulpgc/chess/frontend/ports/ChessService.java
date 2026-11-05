package software.ulpgc.chess.frontend.ports;

import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;

import java.util.List;

/** Outbound port: interaction use cases know neither HTTP nor Swing. */
public interface ChessService {
    List<RoomView> rooms();
    RoomView room(String id);
    SessionView create(String player);
    SessionView join(String id, String player);
    RoomView move(String id, String token, String from, String to, String promotion, long version);
    RoomView resign(String id, String token, long version);
}
