package software.ulpgc.chess.adapters;

import software.ulpgc.chess.domain.model.Board;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.ports.BoardSerializer;

public final class FenBoardSerializer implements BoardSerializer {
    @Override public String serialize(Board board) {
        StringBuilder result = new StringBuilder();
        for (int rank = 8; rank >= 1; rank--) {
            int empty = 0;
            for (int file = 1; file <= 8; file++) {
                Piece piece = board.pieceAt(Square.of(file, rank));
                if (piece == null) empty++;
                else {
                    if (empty > 0) result.append(empty);
                    empty = 0;
                    result.append(piece.symbol());
                }
            }
            if (empty > 0) result.append(empty);
            if (rank > 1) result.append('/');
        }
        return result.toString();
    }
}
