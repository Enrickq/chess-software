package software.ulpgc.chess.backend.model;

import software.ulpgc.chess.contract.RoomView;

/** Persistence model. Credentials must never be included in a public RoomView. */
public record StoredRoom(RoomView view, String whiteTokenHash, String blackTokenHash) {
}
