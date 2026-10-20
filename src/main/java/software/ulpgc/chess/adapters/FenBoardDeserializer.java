package software.ulpgc.chess.adapters;

import software.ulpgc.chess.domain.model.Board;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.ports.BoardDeserializer;
import java.util.HashMap;
import java.util.Map;

/** Completa la descomposicion por filas propuesta en la clase de serializacion. */
public final class FenBoardDeserializer implements BoardDeserializer {
    @Override public Board deserialize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("FEN vacio");
        return deserialize(value.trim().split("\\s+")[0].split("/", -1));
    }

    private Board deserialize(String[] ranks) {
        if (ranks.length != 8) throw new IllegalArgumentException("FEN requiere ocho filas");
        Map<Square, Piece> pieces = new HashMap<>();
        int j = 8;
        for (String rank : ranks) pieces.putAll(piecesIn(j--, rank.toCharArray()));
        return new Board(pieces);
    }

    private Map<Square, Piece> piecesIn(int j, char[] rank) {
        Map<Square, Piece> pieces = new HashMap<>();
        int i = 1;
        boolean previousWasDigit = false;
        for (char order : rank) {
            if (order >= '1' && order <= '8') {
                if (previousWasDigit) throw new IllegalArgumentException("FEN no admite digitos consecutivos");
                i += order - '0';
                previousWasDigit = true;
            }
            else {
                if (i > 8) throw new IllegalArgumentException("Fila FEN demasiado larga");
                pieces.put(Square.of(i++, j), Piece.fromSymbol(order));
                previousWasDigit = false;
            }
            if (i > 9) throw new IllegalArgumentException("Fila FEN demasiado larga");
        }
        if (i != 9) throw new IllegalArgumentException("Cada fila FEN debe cubrir ocho casillas");
        return pieces;
    }
}
