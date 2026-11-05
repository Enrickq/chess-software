package software.ulpgc.chess.frontend.application;

import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.frontend.ports.ChessService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Input use cases, independent of the UI and communication adapter.
 * The caller serializes commands (Swing uses one background worker at a time).
 */
public final class ChessController {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final ChessService service;
    private final Map<String, SessionView> sessions = new HashMap<>();
    private List<RoomView> rooms = List.of();
    private RoomView current;
    private int replayIndex = -1;

    public ChessController(ChessService service) { this.service = Objects.requireNonNull(service); }

    public void listRooms() { rooms = List.copyOf(service.rooms()); }
    public List<RoomView> rooms() { return rooms; }

    public void create(String player) { accept(service.create(requireText(player, "Escribe tu nombre."))); }

    public void join(String id, String player) {
        String roomId = requireText(id, "Selecciona una sala o pega su código.");
        if (sessions.containsKey(roomId)) { watch(roomId); return; }
        accept(service.join(roomId, requireText(player, "Escribe tu nombre.")));
    }

    public void watch(String id) {
        RoomView loaded = service.room(requireText(id, "Selecciona una sala o pega su código."));
        current = loaded;
        replayIndex = -1;
    }

    public void refresh() {
        if (current == null) { listRooms(); return; }
        RoomView loaded = service.room(current.id());
        current = loaded;
        if (replayIndex > loaded.moves().size()) replayIndex = loaded.moves().size();
    }

    public void move(String from, String to, String promotion) {
        SessionView session = requirePlayable();
        RoomView updated = service.move(current.id(), session.token(), from, to,
                promotion == null ? "" : promotion, current.version());
        current = updated;
    }

    public void resign() {
        SessionView session = requirePlayable();
        current = service.resign(current.id(), session.token(), current.version());
    }

    public void replayAt(int index) {
        if (current == null || index < 0 || index > current.moves().size())
            throw new IllegalArgumentException("La posición solicitada no existe en el historial.");
        replayIndex = index;
    }

    public void live() { replayIndex = -1; }

    /** Safe, immutable presentation data. Player tokens never enter the view. */
    public View view() {
        if (current == null) return new View(null, "", true, 0, INITIAL_FEN);
        SessionView session = sessions.get(current.id());
        boolean live = replayIndex == -1;
        int index = live ? current.moves().size() : replayIndex;
        String fen = live ? current.fen() : index == 0 ? INITIAL_FEN : current.moves().get(index - 1).fen();
        return new View(current, session == null ? "" : session.color(), live, index, fen);
    }

    private SessionView requirePlayable() {
        if (current == null) throw new IllegalStateException("Primero entra en una sala.");
        SessionView session = sessions.get(current.id());
        if (session == null) throw new IllegalStateException("Estás viendo la partida como espectador.");
        if (replayIndex != -1) throw new IllegalStateException("Pulsa «En directo» antes de jugar.");
        if (!current.status().equals("ACTIVE")) throw new IllegalStateException("La partida no está en juego.");
        return session;
    }

    private void accept(SessionView session) {
        sessions.put(session.room().id(), session);
        current = session.room();
        replayIndex = -1;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.strip();
    }

    public record View(RoomView room, String color, boolean live, int index, String fen) {
        public boolean participant() { return !color.isEmpty(); }
        public boolean canPlay() { return participant() && live && room != null && room.status().equals("ACTIVE"); }
    }
}
