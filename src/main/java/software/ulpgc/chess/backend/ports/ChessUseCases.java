package software.ulpgc.chess.backend.ports;

import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;

import java.util.List;

/** Inbound application port used by transport adapters. */
public interface ChessUseCases {
    List<RoomView> list();
    RoomView get(String id);
    SessionView create(String player);
    SessionView join(String id, String player);
    RoomView move(String id, String token, long version, String from, String to, String promotion);
    RoomView resign(String id, String token, long version);
}
