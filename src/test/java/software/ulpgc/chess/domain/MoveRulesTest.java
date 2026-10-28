package software.ulpgc.chess.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.ulpgc.chess.adapters.FenPositionDeserializer;
import software.ulpgc.chess.domain.model.*;
import software.ulpgc.chess.domain.service.MoveRules;
import static org.junit.jupiter.api.Assertions.*;

class MoveRulesTest {
    final MoveRules rules = new MoveRules();
    private Position move(Position p, String from, String to) { return rules.move(p,Square.of(from),Square.of(to),null); }
    private Position fen(String text) { return new FenPositionDeserializer().deserialize(text); }
    @Test void openingUpdatesTurnsCountersAndEnPassant() {
        Position first = move(Position.initial(),"e2","e4");
        assertEquals(Color.Black,first.state().turn()); assertEquals(Square.of("e3"),first.state().enPassant());
        Position second = move(first,"e7","e5");
        assertEquals(2,second.state().fullmove());
        Position third = move(second,"g1","f3");
        assertEquals(1,third.state().halfmove()); assertNull(third.state().enPassant());
    }
    @ParameterizedTest @CsvSource({"e2,e5", "a1,a3", "c1,h6", "e1,e2", "e7,e6", "e2,f3", "e2,e2", "d1,h5"})
    void invalidOpeningMoves(String from,String to) { assertThrows(IllegalArgumentException.class, () -> move(Position.initial(),from,to)); }
    @Test void knightJumpsAndCaptureRemovesOnePiece() {
        assertEquals(Piece.WhiteKnight,move(Position.initial(),"b1","c3").board().pieceAt("c3"));
        Position p=move(move(move(Position.initial(),"e2","e4"),"d7","d5"),"e4","d5");
        assertEquals(31,p.board().pieces().size()); assertEquals(Piece.WhitePawn,p.board().pieceAt("d5"));
    }
    @Test void pawnCannotLeapOverOccupiedSquare() {
        Position p=fen("4k3/8/8/8/8/4N3/4P3/4K3 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(p,"e2","e4"));
    }
    @Test void pinnedPieceCannotExposeKing() {
        Position p=fen("k3r3/8/8/8/8/8/4R3/4K3 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(p,"e2","d2"));
        assertDoesNotThrow(() -> move(p,"e2","e8"));
    }
    @Test void kingCannotStepIntoPawnAttackOrNextToKing() {
        Position p=fen("k7/8/8/8/3p4/8/4K3/8 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(p,"e2","e3"));
        Position q=fen("8/8/8/8/4k3/8/4K3/8 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(q,"e2","e3"));
    }
    @Test void promotionDefaultsToQueenAndCanBeKnight() {
        Position p=fen("4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        assertEquals(Piece.WhiteQueen,move(p,"a7","a8").board().pieceAt("a8"));
        assertEquals(Piece.WhiteKnight,rules.move(p,Square.of("a7"),Square.of("a8"),Piece.WhiteKnight).board().pieceAt("a8"));
        assertThrows(IllegalArgumentException.class, () -> rules.move(p,Square.of("a7"),Square.of("a8"),Piece.BlackQueen));
    }
    @Test void blackPromotionAndCapturingPromotion() {
        Position p=fen("4k3/8/8/8/8/8/p7/1R2K3 b - - 0 1");
        assertEquals(Piece.BlackQueen,move(p,"a2","b1").board().pieceAt("b1"));
    }
    @Test void promotionArgumentRequiresLastRank() { assertThrows(IllegalArgumentException.class, () -> rules.move(Position.initial(),Square.of("e2"),Square.of("e4"),Piece.WhiteQueen)); }
    @Test void kingCannotBeCaptured() {
        Position p=fen("4k3/8/8/8/8/8/4R3/K7 w - - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(p,"e2","e8"));
    }
    @Test void unsupportedSpecialMovesAreRejectedExplicitly() {
        Position castle=fen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertThrows(IllegalArgumentException.class, () -> move(castle,"e1","g1"));
        Position ep=fen("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 2");
        assertThrows(IllegalArgumentException.class, () -> move(ep,"e5","d6"));
    }
}
