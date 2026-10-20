package software.ulpgc.chess.ports;
import software.ulpgc.chess.domain.model.Position;

public interface PositionDeserializer { Position deserialize(String value); }
