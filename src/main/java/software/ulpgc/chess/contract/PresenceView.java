package software.ulpgc.chess.contract;

/** Authenticated room refresh with the rival's recent activity. */
public record PresenceView(RoomView room, OpponentPresence opponentPresence) {
    public enum OpponentPresence { UNKNOWN, CONNECTED, DISCONNECTED }
}
