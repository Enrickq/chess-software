package software.ulpgc.chess.domain.model;

import java.util.Objects;

public record State(Color turn, String castling, Square enPassant, int halfmove, int fullmove) {
    public State {
        Objects.requireNonNull(turn, "Turno obligatorio");
        if (castling == null || !(castling.equals("-") || castling.matches("K?Q?k?q?")) || castling.isEmpty())
            throw new IllegalArgumentException("Derechos de enroque invalidos");
        if (enPassant != null && enPassant.rankIndex() != 3 && enPassant.rankIndex() != 6)
            throw new IllegalArgumentException("Objetivo al paso invalido");
        if (enPassant != null && enPassant.rankIndex() != (turn == Color.White ? 6 : 3))
            throw new IllegalArgumentException("Objetivo al paso incompatible con el turno");
        if (halfmove < 0 || fullmove < 1) throw new IllegalArgumentException("Contadores FEN invalidos");
    }

    public static State initial() { return new State(Color.White, "KQkq", null, 0, 1); }
}
