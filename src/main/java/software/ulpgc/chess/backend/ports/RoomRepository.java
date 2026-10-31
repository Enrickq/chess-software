package software.ulpgc.chess.backend.ports;

import software.ulpgc.chess.backend.model.StoredRoom;

import java.util.List;

/** A successful save means that the next application instance can read the room. */
public interface RoomRepository {
    List<StoredRoom> findAll();
    void save(StoredRoom room);
}
