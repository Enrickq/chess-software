package software.ulpgc.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.chess.domain.model.*;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    @Test void boardIsImmutableAndMovingLeavesOriginalUntouched() {
        var map = new HashMap<>(Board.initial().pieces());
        Board board = new Board(map); map.clear();
        Board moved = board.move(Square.of("e2"), Square.of("e4"));
        assertEquals(32, board.pieces().size());
        assertEquals(Piece.WhitePawn, board.pieceAt("e2"));
        assertNull(moved.pieceAt("e2"));
        assertEquals(Piece.WhitePawn, moved.pieceAt("e4"));
        assertThrows(UnsupportedOperationException.class, () -> board.pieces().clear());
    }
    @Test void coordinatesAreOneBased() {
        assertEquals(Square.of("a1"), Square.of(1,1));
        assertEquals(Square.of("h8"), Square.of(8,8));
        assertThrows(IllegalArgumentException.class, () -> Square.of(0,1));
        assertThrows(IllegalArgumentException.class, () -> Square.of(1,9));
    }
    @ParameterizedTest @ValueSource(strings={"", "a0", "i1", "a10", "A1", "11", " e2"})
    void rejectsInvalidSquares(String square) { assertThrows(IllegalArgumentException.class, () -> Square.of(square)); }
    @Test void emptyOriginIsRejected() { assertThrows(IllegalArgumentException.class, () -> Board.initial().move(Square.of("e4"),Square.of("e5"))); }
    @Test void everyPieceHasUniqueFenSymbol() {
        for (Piece piece : Piece.values()) assertEquals(piece, Piece.fromSymbol(piece.symbol()));
    }
}
