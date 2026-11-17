package software.ulpgc.chess.frontend.application;

import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.contract.SessionView;
import software.ulpgc.chess.frontend.ports.ChessService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Input use cases, independent of the UI and communication adapter.
 * The caller serializes operations and waits for their completion before reading or changing state.
 */
public final class ChessController {
    public static final String INITIAL_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private final ChessService service;
    private final Map<String, SessionView> sessions = new HashMap<>();
    private List<RoomView> rooms = List.of();
    private RoomView current;
    private int replayIndex = -1;

    public ChessController(ChessService service) { this.service = Objects.requireNonNull(service); }

    public CompletableFuture<Void> listRooms() {
        return service.rooms().thenAccept(loaded -> rooms = List.copyOf(loaded));
    }
    public List<RoomView> rooms() { return rooms; }

    public CompletableFuture<Void> create(String player) {
        return service.create(requireText(player, "Escribe tu nombre.")).thenAccept(this::accept);
    }

    public CompletableFuture<Void> join(String id, String player) {
        String roomId = requireText(id, "Selecciona una sala o pega su código.");
        if (sessions.containsKey(roomId)) return watch(roomId);
        return service.join(roomId, requireText(player, "Escribe tu nombre.")).thenAccept(this::accept);
    }

    public CompletableFuture<Void> watch(String id) {
        return service.room(requireText(id, "Selecciona una sala o pega su código.")).thenAccept(loaded -> {
            current = loaded;
            replayIndex = -1;
        });
    }

    public CompletableFuture<Void> refresh() {
        if (current == null) return listRooms();
        return service.room(current.id()).thenAccept(loaded -> {
            current = loaded;
            if (replayIndex > loaded.moves().size()) replayIndex = loaded.moves().size();
        });
    }

    public CompletableFuture<Void> move(String from, String to, String promotion) {
        SessionView session = requireActiveSession();
        if (!view().canPlay()) throw new IllegalStateException("No puedes mover con el turno actual. Espera tu turno o actualiza la sala.");
        return service.move(current.id(), session.token(), from, to,
                promotion == null ? "" : promotion, current.version()).thenAccept(updated -> current = updated);
    }

    public CompletableFuture<Void> resign() {
        SessionView session = requireActiveSession();
        return service.resign(current.id(), session.token(), current.version()).thenAccept(updated -> current = updated);
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

    private SessionView requireActiveSession() {
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
        public boolean canResign() { return participant() && live && room != null && room.status().equals("ACTIVE"); }
        public boolean canPlay() {
            if (!canResign() || fen == null) return false;
            String[] fields = fen.strip().split("\\s+");
            if (fields.length < 2) return false;
            return color.equals("White") && fields[1].equals("w")
                    || color.equals("Black") && fields[1].equals("b");
        }
    }
}
