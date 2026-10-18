package software.ulpgc.chess.domain.model;

public enum Color {
    White, Black;

    public Color opposite() { return this == White ? Black : White; }
}
