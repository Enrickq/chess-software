package software.ulpgc.chess.domain.model;

public enum Piece {
    WhitePawn, WhiteKnight, WhiteBishop, WhiteRook, WhiteQueen, WhiteKing,
    BlackPawn, BlackKnight, BlackBishop, BlackRook, BlackQueen, BlackKing;

    private static final String SYMBOLS = "PNBRQKpnbrqk";

    public char symbol() { return SYMBOLS.charAt(ordinal()); }

    public Color color() { return isWhite() ? Color.White : Color.Black; }

    public static Piece fromSymbol(char symbol) {
        int index = SYMBOLS.indexOf(symbol);
        if (index < 0) throw new IllegalArgumentException("Pieza FEN desconocida: " + symbol);
        return values()[index];
    }

    public boolean isWhite() {
        return this.name().startsWith("White");
    }

    public boolean isBlack() {
        return this.name().startsWith("Black");
    }

    public boolean isRook() {
        return this.name().endsWith("Rook");
    }

    public boolean isPawn() {
        return this.name().endsWith("Pawn");
    }

    public boolean isKnight() {
        return this.name().endsWith("Knight");
    }

    public boolean isBishop() {
        return this.name().endsWith("Bishop");
    }

    public boolean isQueen() {
        return this.name().endsWith("Queen");
    }

    public boolean isKing() {
        return this.name().endsWith("King");
    }
}
