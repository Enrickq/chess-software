package software.ulpgc.chess.domain.model;

import java.util.Objects;

public record Position(Board board, State state) {
    public Position {
        Objects.requireNonNull(board, "Tablero obligatorio");
        Objects.requireNonNull(state, "Estado obligatorio");
    }
    public static Position initial() { return new Position(Board.initial(), State.initial()); }
}
