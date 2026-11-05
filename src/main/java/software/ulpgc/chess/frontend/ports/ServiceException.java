package software.ulpgc.chess.frontend.ports;

/** A readable failure from an outbound service; no transport types escape the port. */
public class ServiceException extends RuntimeException {
    public ServiceException(String message) { super(message); }
    public ServiceException(String message, Throwable cause) { super(message, cause); }
}
