package software.ulpgc.chess.domain.model;

import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

public record Board(Map<Square, Piece> pieces) {

    public Board { pieces = Map.copyOf(pieces); }

    public Piece pieceAt(Square square) { return pieces.get(square); }

    public Piece pieceAt(String square) { return pieceAt(Square.of(square)); }

    public static Board initial() {
        return new Board(initialPieces());
    }

    public Board move(Square from, Square to) {
        if (from.equals(to)) throw new IllegalArgumentException("Origen y destino son iguales");
        if (!pieces.containsKey(from)) throw new IllegalArgumentException("No hay pieza en el origen");
        Map<Square, Piece> newPieces = new HashMap<>(this.pieces);
        Piece movingPiece = newPieces.remove(from);
        newPieces.put(to, movingPiece);
        return new Board(Map.copyOf(newPieces));
    }

    private static Map<Square, Piece> initialPieces() {
        return Map.ofEntries(
                entry(Square.of("a1"), Piece.WhiteRook),
                entry(Square.of("b1"), Piece.WhiteKnight),
                entry(Square.of("c1"), Piece.WhiteBishop),
                entry(Square.of("d1"), Piece.WhiteQueen),
                entry(Square.of("e1"), Piece.WhiteKing),
                entry(Square.of("f1"), Piece.WhiteBishop),
                entry(Square.of("g1"), Piece.WhiteKnight),
                entry(Square.of("h1"), Piece.WhiteRook),
                entry(Square.of("a2"), Piece.WhitePawn),
                entry(Square.of("b2"), Piece.WhitePawn),
                entry(Square.of("c2"), Piece.WhitePawn),
                entry(Square.of("d2"), Piece.WhitePawn),
                entry(Square.of("e2"), Piece.WhitePawn),
                entry(Square.of("f2"), Piece.WhitePawn),
                entry(Square.of("g2"), Piece.WhitePawn),
                entry(Square.of("h2"), Piece.WhitePawn),
                entry(Square.of("a7"), Piece.BlackPawn),
                entry(Square.of("b7"), Piece.BlackPawn),
                entry(Square.of("c7"), Piece.BlackPawn),
                entry(Square.of("d7"), Piece.BlackPawn),
                entry(Square.of("e7"), Piece.BlackPawn),
                entry(Square.of("f7"), Piece.BlackPawn),
                entry(Square.of("g7"), Piece.BlackPawn),
                entry(Square.of("h7"), Piece.BlackPawn),
                entry(Square.of("a8"), Piece.BlackRook),
                entry(Square.of("b8"), Piece.BlackKnight),
                entry(Square.of("c8"), Piece.BlackBishop),
                entry(Square.of("d8"), Piece.BlackQueen),
                entry(Square.of("e8"), Piece.BlackKing),
                entry(Square.of("f8"), Piece.BlackBishop),
                entry(Square.of("g8"), Piece.BlackKnight),
                entry(Square.of("h8"), Piece.BlackRook)
        );
    }
}
