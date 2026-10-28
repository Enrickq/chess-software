package software.ulpgc.chess.contract;

public record MoveView(int number, String from, String to, String promotion, String player, String fen, String at) {}
