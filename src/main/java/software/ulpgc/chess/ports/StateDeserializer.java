package software.ulpgc.chess.ports;
import software.ulpgc.chess.domain.model.State;

public interface StateDeserializer { State deserialize(String value); }
