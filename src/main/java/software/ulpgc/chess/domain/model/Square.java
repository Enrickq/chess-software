package software.ulpgc.chess.domain.model;

import java.util.Objects;

public record Square(File file, Rank rank) {
    public Square {
        Objects.requireNonNull(file, "Columna obligatoria");
        Objects.requireNonNull(rank, "Fila obligatoria");
    }

    public static Square of(String value) {
        if (value == null || !value.matches("[a-h][1-8]"))
            throw new IllegalArgumentException("Casilla invalida: debe ser a1..h8");
        return of(value.charAt(0) - 'a' + 1, value.charAt(1) - '0');
    }

    /** Coordenadas de ajedrez: ambas entre 1 y 8, inclusive. */
    public static Square of(int i, int j) {
        if (i < 1 || i > 8 || j < 1 || j > 8)
            throw new IllegalArgumentException("Coordenadas fuera del tablero");
        return new Square(File.values()[i - 1], Rank.values()[j - 1]);
    }

    public int fileIndex() { return file.ordinal() + 1; }
    public int rankIndex() { return rank.ordinal() + 1; }
    @Override public String toString() { return "" + file.symbol() + rank.symbol(); }
}
