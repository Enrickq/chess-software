package software.ulpgc.chess.backend.application;

/** Application failure, independent of the HTTP transport. */
public class ChessException extends RuntimeException {
    public enum Kind { INVALID, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Kind kind;

    public ChessException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
