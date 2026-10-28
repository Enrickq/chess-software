package software.ulpgc.chess.contract;
import java.util.List;

public record RoomView(String id, String white, String black, String status, String fen, long version,
                       String createdAt, List<MoveView> moves) {
    public RoomView { moves = List.copyOf(moves); }
}
