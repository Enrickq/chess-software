package software.ulpgc.chess.ports;
import software.ulpgc.chess.domain.model.Board;

public interface BoardDeserializer { Board deserialize(String value); }
