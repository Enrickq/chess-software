package software.ulpgc.chess.domain.model;

public enum File {
    A('a'),
    B('b'),
    C('c'),
    D('d'),
    E('e'),
    F('f'),
    G('g'),
    H('h');

    private final char symbol;

    File(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return this.symbol;
    }
}