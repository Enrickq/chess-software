package software.ulpgc.chess.domain.model;

public enum Rank {
    ONE('1'),
    TWO('2'),
    THREE('3'),
    FOUR('4'),
    FIVE('5'),
    SIX('6'),
    SEVEN('7'),
    EIGHT('8');

    private final char symbol;

    Rank(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return this.symbol;
    }
}
