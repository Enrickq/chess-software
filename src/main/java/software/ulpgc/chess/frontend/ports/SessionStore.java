package software.ulpgc.chess.frontend.ports;

import java.util.Optional;

/** Local storage for the last room entered as a player. */
public interface SessionStore {
    Optional<PlayerSession> load();
    void save(PlayerSession session);

    SessionStore TRANSIENT = new SessionStore() {
        @Override public Optional<PlayerSession> load() { return Optional.empty(); }
        @Override public void save(PlayerSession session) { }
    };

    record PlayerSession(String roomId, String token, String color) { }
}
