package software.ulpgc.chess.contract;

/** Token solo presente en la respuesta privada de creacion/union. */
public record SessionView(RoomView room, String token, String color) {}
