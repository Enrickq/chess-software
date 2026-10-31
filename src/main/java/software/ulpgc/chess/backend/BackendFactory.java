package software.ulpgc.chess.backend;

import software.ulpgc.chess.adapters.FenPositionDeserializer;
import software.ulpgc.chess.adapters.FenPositionSerializer;
import software.ulpgc.chess.backend.application.ChessService;
import software.ulpgc.chess.backend.ports.RoomRepository;

/** Assembles the chosen adapters without coupling the application to their classes. */
public final class BackendFactory {
    private BackendFactory() { }

    public static ChessService service(RoomRepository repository) {
        return new ChessService(repository, new FenPositionSerializer(), new FenPositionDeserializer());
    }
}
