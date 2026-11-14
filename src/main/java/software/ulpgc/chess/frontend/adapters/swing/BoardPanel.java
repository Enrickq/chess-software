package software.ulpgc.chess.frontend.adapters.swing;

import software.ulpgc.chess.adapters.FenBoardDeserializer;
import software.ulpgc.chess.domain.model.Board;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.domain.model.Square;
import software.ulpgc.chess.frontend.application.ChessController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/** Paints the professor's Board model; chess rules remain on the server. */
public final class BoardPanel extends JPanel {
    private static final Color LIGHT = new Color(239, 231, 211);
    private static final Color DARK = new Color(104, 139, 128);
    private Board board = new FenBoardDeserializer().deserialize(ChessController.INITIAL_FEN);
    private boolean reversed;
    private String selected;
    private String lastFrom;
    private String lastTo;
    private Consumer<String> onSquare = square -> {};

    public BoardPanel() {
        setPreferredSize(new Dimension(550, 550));
        setMinimumSize(new Dimension(310, 310));
        setBackground(new Color(246, 248, 248));
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String square = squareAt(e.getX(), e.getY());
                if (square != null) onSquare.accept(square);
            }
        });
        getAccessibleContext().setAccessibleName("Tablero de ajedrez. Usa también los campos de origen y destino para jugar con teclado.");
    }

    public void onSquare(Consumer<String> handler) { onSquare = handler; }
    public void selected(String square) { selected = square; repaint(); }
    public Piece pieceAt(String square) { return board.pieces().get(Square.of(square)); }

    public void display(String fen, boolean reversed, String from, String to) {
        board = new FenBoardDeserializer().deserialize(fen);
        this.reversed = reversed;
        lastFrom = from;
        lastTo = to;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int cell = cellSize(), left = (getWidth() - cell * 8) / 2, top = (getHeight() - cell * 8) / 2;
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, cell / 5)));
            for (int row = 0; row < 8; row++) for (int column = 0; column < 8; column++) {
                String square = square(column, row);
                int x = left + column * cell, y = top + row * cell;
                g.setColor((row + column) % 2 == 0 ? LIGHT : DARK);
                g.fillRect(x, y, cell, cell);
                if (square.equals(lastFrom) || square.equals(lastTo)) {
                    g.setColor(new Color(242, 191, 67, 95));
                    g.fillRect(x, y, cell, cell);
                }
                if (square.equals(selected)) {
                    g.setColor(new Color(255, 210, 70));
                    g.setStroke(new BasicStroke(4));
                    g.drawRect(x + 2, y + 2, cell - 4, cell - 4);
                }
                Piece piece = board.pieces().get(Square.of(square));
                if (piece != null) drawPiece(g, piece, x, y, cell);
            }
            g.setColor(new Color(49, 67, 72));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, cell / 5)));
            FontMetrics metrics = g.getFontMetrics();
            for (int index = 0; index < 8; index++) {
                String file = square(index, 7).substring(0, 1), rank = square(0, index).substring(1);
                g.drawString(file, left + index * cell + (cell - metrics.stringWidth(file)) / 2, top + cell * 8 + 19);
                g.drawString(rank, left - 18, top + index * cell + (cell + metrics.getAscent()) / 2 - 2);
            }
        } finally { g.dispose(); }
    }

    private void drawPiece(Graphics2D g, Piece piece, int x, int y, int cell) {
        String glyph = switch (piece) {
            case WhiteKing -> "\u2654"; case WhiteQueen -> "\u2655";
            case WhiteRook -> "\u2656"; case WhiteBishop -> "\u2657";
            case WhiteKnight -> "\u2658"; case WhitePawn -> "\u2659";
            case BlackKing -> "\u265a"; case BlackQueen -> "\u265b";
            case BlackRook -> "\u265c"; case BlackBishop -> "\u265d";
            case BlackKnight -> "\u265e"; case BlackPawn -> "\u265f";
        };
        Font font = pieceFont(glyph.charAt(0), Math.round(cell * .77f));
        if (font == null) {
            g.setColor(piece.isWhite() ? Color.WHITE : new Color(28, 44, 49));
            g.fillOval(x + cell / 6, y + cell / 6, cell * 2 / 3, cell * 2 / 3);
            g.setColor(piece.isWhite() ? new Color(28, 44, 49) : Color.WHITE);
            glyph = piece.isKnight() ? "N" : piece.name().substring(5, 6);
            font = new Font(Font.SANS_SERIF, Font.BOLD, cell / 2);
        } else g.setColor(new Color(21, 37, 43));
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        int gx = x + (cell - metrics.stringWidth(glyph)) / 2;
        int gy = y + (cell - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
        g.drawString(glyph, gx, gy);
    }

    private Font pieceFont(char glyph, int size) {
        for (String family : new String[]{"Segoe UI Symbol", "DejaVu Sans", Font.SERIF, Font.DIALOG}) {
            Font font = new Font(family, Font.PLAIN, size);
            if (font.canDisplay(glyph)) return font;
        }
        return null;
    }

    private int cellSize() { return Math.max(1, (Math.min(getWidth(), getHeight()) - 48) / 8); }
    private String square(int column, int row) {
        int file = reversed ? 7 - column : column;
        int rank = reversed ? row + 1 : 8 - row;
        return "" + (char) ('a' + file) + rank;
    }
    private String squareAt(int x, int y) {
        int cell = cellSize(), left = (getWidth() - cell * 8) / 2, top = (getHeight() - cell * 8) / 2;
        if (x < left || y < top || x >= left + cell * 8 || y >= top + cell * 8) return null;
        return square((x - left) / cell, (y - top) / cell);
    }
    @Override public String getToolTipText(MouseEvent e) {
        String square = squareAt(e.getX(), e.getY());
        if (square == null) return null;
        Piece piece = pieceAt(square);
        return square + (piece == null ? " · vacía" : " · " + piece.name());
    }
}
