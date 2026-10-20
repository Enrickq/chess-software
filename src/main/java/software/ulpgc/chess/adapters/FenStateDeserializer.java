package software.ulpgc.chess.adapters;

import software.ulpgc.chess.domain.model.Color;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.domain.model.State;
import software.ulpgc.chess.ports.StateDeserializer;

public final class FenStateDeserializer implements StateDeserializer {
    @Override public State deserialize(String value) {
        if (value == null) throw new IllegalArgumentException("Estado FEN obligatorio");
        String[] fields = value.trim().split("\\s+");
        if (fields.length != 5) throw new IllegalArgumentException("Estado FEN requiere cinco campos");
        if (!fields[3].matches("[0-9]+") || !fields[4].matches("[0-9]+"))
            throw new IllegalArgumentException("Contadores FEN deben ser enteros sin signo");
        Color turn = switch (fields[0]) {
            case "w" -> Color.White;
            case "b" -> Color.Black;
            default -> throw new IllegalArgumentException("Turno FEN debe ser w o b");
        };
        try {
            return new State(turn, fields[1], fields[2].equals("-") ? null : Square.of(fields[2]),
                    Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Contadores FEN deben ser enteros", ex); }
    }
}
