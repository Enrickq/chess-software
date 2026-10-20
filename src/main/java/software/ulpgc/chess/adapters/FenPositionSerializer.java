package software.ulpgc.chess.adapters;

import software.ulpgc.chess.domain.model.Color;
import software.ulpgc.chess.domain.model.Position;
import software.ulpgc.chess.domain.model.State;
import software.ulpgc.chess.ports.BoardSerializer;
import software.ulpgc.chess.ports.PositionSerializer;

public final class FenPositionSerializer implements PositionSerializer {
    private final BoardSerializer boards;
    public FenPositionSerializer() { this(new FenBoardSerializer()); }
    public FenPositionSerializer(BoardSerializer boards) { this.boards = boards; }
    @Override public String serialize(Position position) {
        State s = position.state();
        return boards.serialize(position.board()) + " " + (s.turn() == Color.White ? "w" : "b") + " "
                + s.castling() + " " + (s.enPassant() == null ? "-" : s.enPassant()) + " " + s.halfmove() + " " + s.fullmove();
    }
}
