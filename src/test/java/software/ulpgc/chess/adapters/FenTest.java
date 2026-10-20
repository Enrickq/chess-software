package software.ulpgc.chess.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.chess.domain.model.*;
import static org.junit.jupiter.api.Assertions.*;

class FenTest {
    static final String INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    final FenBoardDeserializer boards = new FenBoardDeserializer();
    final FenPositionDeserializer positions = new FenPositionDeserializer();

    @Test void initialBoardMatchesAllThirtyTwoPieces() { assertEquals(Board.initial(), boards.deserialize(INITIAL)); }
    @Test void emptyBoard() { assertTrue(boards.deserialize("8/8/8/8/8/8/8/8").pieces().isEmpty()); }
    @Test void ignoresMetadataOnlyWhenReadingBoard() {
        Board b = boards.deserialize("4k3/8/8/8/8/8/8/4K3 b KQkq e3 5 12");
        assertEquals(2,b.pieces().size()); assertEquals(Piece.WhiteKing,b.pieceAt("e1")); assertEquals(Piece.BlackKing,b.pieceAt("e8"));
    }
    @Test void fullFenRoundTrip() { assertEquals(INITIAL, new FenPositionSerializer().serialize(positions.deserialize(INITIAL))); }
    @Test void readsStateFields() {
        State s = positions.deserialize("4k3/8/8/8/8/8/8/4K3 b - e3 5 12").state();
        assertEquals(Color.Black,s.turn()); assertEquals("-",s.castling()); assertEquals(Square.of("e3"),s.enPassant());
        assertEquals(5,s.halfmove()); assertEquals(12,s.fullmove());
    }
    @ParameterizedTest @ValueSource(strings={"", "8/8", "8/8/8/8/8/8/8/7", "8/8/8/8/8/8/8/9", "8/8/8/8/8/8/8/8P", "8/8/8/8/8/8/8/0K7", "8/8/8/8/8/8/8/7X", "8/8/8/8/8/8/8/8/"})
    void rejectsMalformedBoards(String text) { assertThrows(IllegalArgumentException.class, () -> boards.deserialize(text)); }
    @ParameterizedTest @ValueSource(strings={"x - - 0 1", "w KK - 0 1", "w - e4 0 1", "w - - -1 1", "w - - 0 0", "w - - 0 a", "w - - 0 1 extra"})
    void rejectsMalformedState(String text) { assertThrows(IllegalArgumentException.class, () -> positions.deserialize("8/8/8/8/8/8/8/8 " + text)); }

    @Test void rejectsNonCanonicalEmptyRunsAndSignedCounters() {
        assertThrows(IllegalArgumentException.class, () -> boards.deserialize("44/8/8/8/8/8/8/8"));
        assertThrows(IllegalArgumentException.class, () -> positions.deserialize("8/8/8/8/8/8/8/8 w - - +0 1"));
        assertThrows(IllegalArgumentException.class, () -> positions.deserialize("8/8/8/8/8/8/8/8 w - e3 0 1"));
    }
}
