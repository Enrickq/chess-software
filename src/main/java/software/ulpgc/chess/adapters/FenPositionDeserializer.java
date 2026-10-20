package software.ulpgc.chess.adapters;

import software.ulpgc.chess.domain.model.Position;
import software.ulpgc.chess.ports.BoardDeserializer;
import software.ulpgc.chess.ports.PositionDeserializer;
import software.ulpgc.chess.ports.StateDeserializer;

public final class FenPositionDeserializer implements PositionDeserializer {
    private final BoardDeserializer boards;
    private final StateDeserializer states;
    public FenPositionDeserializer() { this(new FenBoardDeserializer(), new FenStateDeserializer()); }
    public FenPositionDeserializer(BoardDeserializer boards, StateDeserializer states) { this.boards = boards; this.states = states; }
    @Override public Position deserialize(String value) {
        if (value == null) throw new IllegalArgumentException("FEN obligatorio");
        String[] fields = value.trim().split("\\s+", 2);
        if (fields.length != 2) throw new IllegalArgumentException("Posicion FEN requiere seis campos");
        return new Position(boards.deserialize(fields[0]), states.deserialize(fields[1]));
    }
}
