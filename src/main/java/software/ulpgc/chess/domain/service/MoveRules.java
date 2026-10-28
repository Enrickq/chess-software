package software.ulpgc.chess.domain.service;

import software.ulpgc.chess.domain.model.*;
import java.util.HashMap;
import java.util.Map;

/** Subconjunto explicito: movimiento ordinario, jaque y promocion; sin enroque ni captura al paso. */
public final class MoveRules {
    public Position move(Position position, Square from, Square to, Piece promotion) {
        Board board = position.board();
        Piece piece = board.pieceAt(from);
        Piece target = board.pieceAt(to);
        if (piece == null) throw new IllegalArgumentException("No hay pieza en el origen");
        if (piece.color() != position.state().turn()) throw new IllegalArgumentException("No es el turno de esa pieza");
        if (from.equals(to)) throw new IllegalArgumentException("El destino debe ser distinto del origen");
        if (target != null && target.color() == piece.color()) throw new IllegalArgumentException("No puedes capturar tu propia pieza");
        if (target != null && target.isKing()) throw new IllegalArgumentException("El rey no se captura");
        if (!validGeometry(board, piece, from, to, false))
            throw new IllegalArgumentException("Movimiento invalido (enroque y captura al paso no disponibles)");
        boolean promotes = piece.isPawn() && (to.rankIndex() == 1 || to.rankIndex() == 8);
        if (promotion != null && !promotes) throw new IllegalArgumentException("Solo se promociona en la ultima fila");
        Piece result = promotes ? promotion(piece, promotion) : piece;
        Map<Square, Piece> pieces = new HashMap<>(board.pieces());
        pieces.remove(from);
        pieces.put(to, result);
        Board next = new Board(pieces);
        if (isInCheck(next, piece.color())) throw new IllegalArgumentException("El movimiento deja tu rey en jaque");
        State old = position.state();
        Square enPassant = piece.isPawn() && Math.abs(to.rankIndex() - from.rankIndex()) == 2
                ? Square.of(from.fileIndex(), (from.rankIndex() + to.rankIndex()) / 2) : null;
        State state = new State(old.turn().opposite(), castlingAfter(old.castling(), from, to, piece), enPassant,
                piece.isPawn() || target != null ? 0 : Math.addExact(old.halfmove(), 1),
                old.turn() == Color.Black ? Math.addExact(old.fullmove(), 1) : old.fullmove());
        return new Position(next, state);
    }

    public boolean isInCheck(Board board, Color color) {
        Square king = board.pieces().entrySet().stream()
                .filter(e -> e.getValue().isKing() && e.getValue().color() == color)
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Falta el rey del jugador"));
        return board.pieces().entrySet().stream()
                .filter(e -> e.getValue().color() != color)
                .anyMatch(e -> validGeometry(board, e.getValue(), e.getKey(), king, true));
    }

    private boolean validGeometry(Board board, Piece piece, Square from, Square to, boolean attack) {
        int dx = to.fileIndex() - from.fileIndex(), dy = to.rankIndex() - from.rankIndex();
        if (dx == 0 && dy == 0) return false;
        int ax = Math.abs(dx), ay = Math.abs(dy);
        if (piece.isKnight()) return ax * ay == 2;
        if (piece.isKing()) return Math.max(ax, ay) == 1;
        if (piece.isPawn()) return pawnGeometry(board, piece, from, to, dx, dy, attack);
        boolean diagonal = ax == ay, straight = dx == 0 || dy == 0;
        boolean shape = piece.isBishop() ? diagonal : piece.isRook() ? straight : diagonal || straight;
        return shape && clearPath(board, from, to);
    }

    private boolean pawnGeometry(Board board, Piece piece, Square from, Square to, int dx, int dy, boolean attack) {
        int direction = piece.isWhite() ? 1 : -1;
        if (attack) return Math.abs(dx) == 1 && dy == direction;
        if (Math.abs(dx) == 1 && dy == direction) return board.pieceAt(to) != null;
        if (dx != 0 || board.pieceAt(to) != null) return false;
        if (dy == direction) return true;
        return dy == 2 * direction && from.rankIndex() == (piece.isWhite() ? 2 : 7)
                && board.pieceAt(Square.of(from.fileIndex(), from.rankIndex() + direction)) == null;
    }

    private boolean clearPath(Board board, Square from, Square to) {
        int dx = Integer.signum(to.fileIndex() - from.fileIndex());
        int dy = Integer.signum(to.rankIndex() - from.rankIndex());
        int x = from.fileIndex() + dx, y = from.rankIndex() + dy;
        while (x != to.fileIndex() || y != to.rankIndex()) {
            if (board.pieceAt(Square.of(x, y)) != null) return false;
            x += dx; y += dy;
        }
        return true;
    }

    private Piece promotion(Piece pawn, Piece selected) {
        if (selected == null) return pawn.isWhite() ? Piece.WhiteQueen : Piece.BlackQueen;
        if (selected.color() != pawn.color() || selected.isKing() || selected.isPawn())
            throw new IllegalArgumentException("Promocion debe ser dama, torre, alfil o caballo del mismo color");
        return selected;
    }

    private String castlingAfter(String rights, Square from, Square to, Piece piece) {
        String result = rights.equals("-") ? "" : rights;
        if (piece.isKing()) result = piece.isWhite() ? result.replace("K", "").replace("Q", "") : result.replace("k", "").replace("q", "");
        for (Square square : new Square[]{from, to}) result = switch (square.toString()) {
            case "a1" -> result.replace("Q", ""); case "h1" -> result.replace("K", "");
            case "a8" -> result.replace("q", ""); case "h8" -> result.replace("k", ""); default -> result;
        };
        return result.isEmpty() ? "-" : result;
    }
}
