package software.ulpgc.chess.frontend.adapters.swing;

import software.ulpgc.chess.contract.MoveView;
import software.ulpgc.chess.contract.RoomView;
import software.ulpgc.chess.domain.model.Piece;
import software.ulpgc.chess.frontend.application.ChessController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.concurrent.ExecutionException;

/** Swing input adapter. Construction and loadView are offline and can be rendered headlessly. */
public final class ChessPanel extends JPanel {
    private static final Color INK = new Color(25, 47, 56);
    private static final Color TEAL = new Color(27, 112, 103);
    private static final Color MUTED = new Color(93, 109, 115);
    private final ChessController controller;
    private final BoardPanel board = new BoardPanel();
    private final JTextField player = new JTextField(14);
    private final JTextField invitation = new JTextField(14);
    private final JTextField roomCode = new JTextField();
    private final JTextField origin = new JTextField(3);
    private final JTextField destination = new JTextField(3);
    private final DefaultListModel<RoomView> roomModel = new DefaultListModel<>();
    private final JList<RoomView> roomList = new JList<>(roomModel);
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> history = new JList<>(historyModel);
    private final JLabel connection = label("Conexión pendiente", 12, MUTED);
    private final JLabel title = label("Empieza una partida", 25, INK);
    private final JLabel subtitle = label("Crea una sala o entra con su código.", 13, MUTED);
    private final JLabel whitePlayer = label("Blancas · —", 14, INK);
    private final JLabel blackPlayer = label("Negras · —", 14, INK);
    private final JLabel phase = label("SIN SALA", 12, TEAL);
    private final JLabel replayLabel = label("Posición inicial", 12, MUTED);
    private final JTextArea message = new JTextArea("Escribe tu nombre para crear una sala. También puedes observar una partida.");
    private final JSlider replay = new JSlider(0, 0, 0);
    private final JButton create = button("Crear sala", true);
    private final JButton join = button("Unirme", true);
    private final JButton watch = button("Observar", false);
    private final JButton refresh = button("Actualizar salas", false);
    private final JButton copy = button("Copiar código", false);
    private final JButton move = button("Mover", true);
    private final JButton resign = button("Abandonar partida", false);
    private final JButton previous = button("←", false);
    private final JButton next = button("→", false);
    private final JButton live = button("En directo", true);
    private final Timer timer;
    private boolean busy;
    private boolean dialogOpen;
    private boolean updating;
    private String selected;
    private ChessController.View view;

    public ChessPanel(ChessController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(0, 0));
        setBackground(new Color(246, 248, 248));
        setPreferredSize(new Dimension(1180, 790));
        add(header(), BorderLayout.NORTH);
        add(lobby(), BorderLayout.WEST);
        add(game(), BorderLayout.CENTER);
        add(details(), BorderLayout.EAST);
        add(footer(), BorderLayout.SOUTH);
        timer = new Timer(2000, e -> {
            if (!dialogOpen) execute(controller::refresh, null);
        });
        wireActions();
        loadView();
    }

    /** Starts HTTP polling; call only when opening the interactive window. */
    public void startPolling() {
        execute(() -> { controller.listRooms(); controller.refresh(); }, "Conectado. Elige una sala o crea la tuya.");
        timer.start();
    }
    public void stopPolling() { timer.stop(); }

    /** Refreshes visible components from already loaded use-case state, without network access. */
    public void loadView() {
        updating = true;
        try {
            ChessController.View previousView = view;
            view = controller.view();
            if (previousView != null && !previousView.fen().equals(view.fen())) clearSelection();
            RoomView room = view.room();
            RoomView selectedRoom = roomList.getSelectedValue();
            roomModel.clear();
            for (RoomView item : controller.rooms())
                roomModel.addElement(room != null && item.id().equals(room.id()) ? room : item);
            if (room != null && controller.rooms().stream().noneMatch(item -> item.id().equals(room.id())))
                roomModel.addElement(room);
            if (selectedRoom != null) for (int i = 0; i < roomModel.size(); i++)
                if (roomModel.get(i).id().equals(selectedRoom.id())) roomList.setSelectedIndex(i);
            title.setText(room == null ? "Empieza una partida" : view.live() ? "Tablero en directo" : "Explora la partida");
            phase.setText(room == null ? "SIN SALA" : status(room.status()).toUpperCase());
            subtitle.setText(room == null ? "Crea una sala o entra con su código." : subtitle(room));
            roomCode.setText(room == null ? "" : room.id());
            roomCode.setCaretPosition(0);
            roomCode.setToolTipText(room == null ? "" : room.id());
            whitePlayer.setText("Blancas · " + (room == null ? "—" : room.white()));
            blackPlayer.setText("Negras · " + (room == null || room.black().isBlank() ? "Esperando rival" : room.black()));
            whitePlayer.setToolTipText(whitePlayer.getText());
            blackPlayer.setToolTipText(blackPlayer.getText());
            int size = room == null ? 0 : room.moves().size();
            replay.setMaximum(size);
            replay.setValue(view.index());
            replayLabel.setText((view.live() ? "En directo" : "Reproducción") + " · " + view.index() + " / " + size + " jugadas");
            historyModel.clear();
            historyModel.addElement("0   Posición inicial");
            if (room != null) for (MoveView item : room.moves())
                historyModel.addElement(item.number() + "   " + item.from() + " → " + item.to()
                        + (item.promotion().isBlank() ? "" : " = " + item.promotion().toUpperCase()) + "   · " + item.player());
            history.setSelectedIndex(view.index());
            history.ensureIndexIsVisible(view.index());
            MoveView last = room == null || view.index() == 0 ? null : room.moves().get(view.index() - 1);
            board.display(view.fen(), view.color().equals("Black"), last == null ? null : last.from(), last == null ? null : last.to());
            updateEnabled();
        } finally { updating = false; }
    }

    /** Supplies factual context when presenting a snapshot loaded outside the interactive window. */
    public void loadView(String connectionStatus, String notice) {
        loadView();
        connection.setText(connectionStatus);
        notice(notice, false);
    }

    public BoardPanel boardComponent() { return board; }

    private JComponent header() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(INK);
        panel.setBorder(new EmptyBorder(18, 24, 18, 24));
        JPanel identity = new JPanel(new GridLayout(2, 1, 0, 3));
        identity.setOpaque(false);
        identity.add(label("CHESS / SOFTWARE", 22, Color.WHITE));
        identity.add(label("Juega, comparte y recorre cada movimiento", 12, new Color(197, 217, 221)));
        panel.add(identity, BorderLayout.WEST);
        connection.setForeground(new Color(216, 231, 233));
        panel.add(connection, BorderLayout.EAST);
        return panel;
    }

    private JComponent lobby() {
        JPanel panel = vertical();
        panel.setPreferredSize(new Dimension(245, 610));
        panel.setBorder(new EmptyBorder(23, 20, 15, 18));
        panel.add(label("01  ENTRA A JUGAR", 12, TEAL));
        panel.add(Box.createVerticalStrut(16));
        panel.add(label("Tu nombre", 13, INK));
        field(panel, player);
        player.setToolTipText("Nombre visible para los demás jugadores");
        panel.add(create);
        panel.add(Box.createVerticalStrut(22));
        panel.add(label("Código de sala", 13, INK));
        field(panel, invitation);
        invitation.setToolTipText("Pega el código compartido o selecciona una sala de la lista");
        JPanel choices = new JPanel(new GridLayout(1, 2, 7, 0));
        choices.setOpaque(false);
        choices.add(join);
        choices.add(watch);
        choices.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        choices.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(choices);
        panel.add(Box.createVerticalStrut(23));
        panel.add(label("SALAS E HISTÓRICO", 12, TEAL));
        panel.add(Box.createVerticalStrut(9));
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFixedCellHeight(70);
        roomList.setCellRenderer(new RoomRenderer());
        roomList.setBackground(new Color(247, 249, 249));
        JScrollPane scroll = new JScrollPane(roomList);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(223, 230, 230)));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(10));
        panel.add(refresh);
        return panel;
    }

    private JComponent game() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(23, 8, 12, 8));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 5));
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(0, 18, 4, 0));
        heading.add(title);
        heading.add(subtitle);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(board, BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        controls.setOpaque(false);
        controls.add(label("Origen", 12, MUTED));
        controls.add(origin);
        controls.add(label("Destino", 12, MUTED));
        controls.add(destination);
        controls.add(move);
        origin.setToolTipText("Casilla de origen, por ejemplo e2");
        destination.setToolTipText("Casilla de destino, por ejemplo e4");
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent details() {
        JPanel panel = vertical();
        panel.setPreferredSize(new Dimension(292, 610));
        panel.setBorder(new EmptyBorder(23, 16, 15, 20));
        panel.add(label("02  TU SALA", 12, TEAL));
        panel.add(Box.createVerticalStrut(15));
        panel.add(phase);
        panel.add(Box.createVerticalStrut(10));
        panel.add(whitePlayer);
        panel.add(Box.createVerticalStrut(8));
        panel.add(blackPlayer);
        panel.add(Box.createVerticalStrut(15));
        roomCode.setEditable(false);
        roomCode.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        field(panel, roomCode);
        panel.add(copy);
        panel.add(Box.createVerticalStrut(23));
        panel.add(label("03  HISTORIAL INTERACTIVO", 12, TEAL));
        panel.add(Box.createVerticalStrut(10));
        panel.add(replayLabel);
        replay.setOpaque(false);
        replay.setAlignmentX(LEFT_ALIGNMENT);
        replay.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        replay.getAccessibleContext().setAccessibleName("Posición del historial");
        panel.add(replay);
        JPanel navigation = new JPanel(new GridLayout(1, 3, 5, 0));
        navigation.setOpaque(false);
        navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
        navigation.setAlignmentX(LEFT_ALIGNMENT);
        previous.setToolTipText("Posición anterior");
        next.setToolTipText("Posición siguiente");
        navigation.add(previous);
        navigation.add(next);
        navigation.add(live);
        panel.add(navigation);
        panel.add(Box.createVerticalStrut(10));
        history.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        history.setFixedCellHeight(29);
        history.setSelectionBackground(new Color(217, 235, 228));
        history.setSelectionForeground(INK);
        JScrollPane scroll = new JScrollPane(history);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(223, 230, 230)));
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(12));
        panel.add(resign);
        return panel;
    }

    private JComponent footer() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(231, 239, 237));
        panel.setBorder(new EmptyBorder(12, 22, 12, 22));
        message.setEditable(false);
        message.setFocusable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setRows(2);
        message.setOpaque(false);
        message.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        message.setForeground(INK);
        panel.add(message);
        return panel;
    }

    private void wireActions() {
        create.addActionListener(e -> {
            String name = player.getText();
            execute(() -> controller.create(name), "Sala creada. Comparte el código y espera a tu rival.");
        });
        join.addActionListener(e -> {
            String id = selectedRoomId(), name = player.getText();
            execute(() -> controller.join(id, name), "Has entrado en la sala. Las blancas empiezan.");
        });
        watch.addActionListener(e -> {
            String id = selectedRoomId();
            execute(() -> controller.watch(id), "Sala abierta. Usa el historial para recorrer la partida.");
        });
        refresh.addActionListener(e -> execute(() -> { controller.listRooms(); controller.refresh(); }, "Salas actualizadas."));
        copy.addActionListener(e -> {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(roomCode.getText()), null);
                notice("Código copiado. Compártelo con tu rival.", false);
            } catch (RuntimeException ex) { notice("No se pudo copiar. Selecciona el código y cópialo manualmente.", true); }
        });
        board.onSquare(this::selectSquare);
        move.addActionListener(e -> submitMove(origin.getText().strip().toLowerCase(), destination.getText().strip().toLowerCase()));
        destination.addActionListener(e -> move.doClick());
        roomList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && roomList.getSelectedValue() != null)
                invitation.setText(roomList.getSelectedValue().id());
        });
        replay.addChangeListener(e -> {
            if (!updating && !busy && view.room() != null) showReplay(replay.getValue());
        });
        history.addListSelectionListener(e -> {
            if (!updating && !busy && !e.getValueIsAdjusting() && view.room() != null && history.getSelectedIndex() >= 0)
                showReplay(history.getSelectedIndex());
        });
        previous.addActionListener(e -> showReplay(Math.max(0, view.index() - 1)));
        next.addActionListener(e -> showReplay(Math.min(view.room().moves().size(), view.index() + 1)));
        live.addActionListener(e -> { controller.live(); clearSelection(); loadView(); notice("Vista en directo. Selecciona origen y destino para mover.", false); });
        resign.addActionListener(e -> {
            int answer;
            dialogOpen = true;
            try {
                answer = JOptionPane.showConfirmDialog(this, "¿Abandonar la partida? La victoria será para tu rival.",
                        "Abandonar partida", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            } finally { dialogOpen = false; }
            if (answer == JOptionPane.YES_OPTION)
                execute(controller::resign, "Has abandonado la partida. El historial sigue disponible.");
        });
    }

    private void selectSquare(String square) {
        if (busy) return;
        if (!view.canPlay()) { notice(view.live() ? "Espera a tu rival o entra como jugador para mover." : "Pulsa «En directo» para volver a jugar.", false); return; }
        Piece piece = board.pieceAt(square);
        boolean own = piece != null && piece.isWhite() == view.color().equals("White");
        if (selected == null || own) {
            if (!own) { notice("Selecciona una pieza de tu color.", false); return; }
            selected = square;
            origin.setText(square);
            destination.setText("");
            board.selected(square);
            notice("Origen " + square + ". Selecciona la casilla de destino.", false);
        } else {
            destination.setText(square);
            submitMove(selected, square);
        }
    }

    private void submitMove(String from, String to) {
        if (busy) return;
        if (!from.matches("[a-h][1-8]") || !to.matches("[a-h][1-8]")) {
            notice("Escribe dos casillas válidas, por ejemplo e2 y e4.", true);
            return;
        }
        String promotion = "";
        Piece piece = board.pieceAt(from);
        if (piece != null && piece.isPawn() && (to.endsWith("8") || to.endsWith("1"))) {
            String[] choices = {"Dama", "Torre", "Alfil", "Caballo"};
            int answer;
            dialogOpen = true;
            try {
                answer = JOptionPane.showOptionDialog(this, "Elige la pieza de promoción.", "Promoción del peón",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
            } finally { dialogOpen = false; }
            if (answer < 0) return;
            promotion = new String[]{"q", "r", "b", "n"}[answer];
        }
        String chosenPromotion = promotion;
        execute(() -> controller.move(from, to, chosenPromotion), "Movimiento registrado: " + from + " → " + to + ".");
    }

    private void showReplay(int index) {
        if (busy || view.room() == null) return;
        controller.replayAt(index);
        clearSelection();
        loadView();
        notice("Reproducción del historial. La partida continúa actualizándose; pulsa «En directo» para jugar.", false);
    }

    private void execute(Runnable action, String success) {
        if (busy) return;
        busy = true;
        updateEnabled();
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { action.run(); return null; }
            @Override protected void done() {
                busy = false;
                try {
                    get();
                    connection.setText("● Servidor conectado");
                    if (success != null) { clearSelection(); notice(success, false); }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    notice("La operación se interrumpió. Puedes reintentar.", true);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    connection.setText("● Última petición fallida");
                    notice(cause.getMessage() == null ? "No se pudo completar la operación." : cause.getMessage(), true);
                } finally { loadView(); }
            }
        }.execute();
    }

    private void updateEnabled() {
        if (view == null) return;
        for (JComponent component : List.of(create, join, watch, refresh, roomList)) component.setEnabled(!busy);
        boolean hasRoom = view.room() != null;
        copy.setEnabled(hasRoom);
        replay.setEnabled(!busy && hasRoom);
        history.setEnabled(!busy && hasRoom);
        previous.setEnabled(!busy && hasRoom && view.index() > 0);
        next.setEnabled(!busy && hasRoom && view.index() < view.room().moves().size());
        live.setEnabled(!busy && hasRoom && !view.live());
        move.setEnabled(!busy && view.canPlay());
        origin.setEnabled(view.canPlay());
        destination.setEnabled(view.canPlay());
        resign.setEnabled(!busy && view.canPlay());
    }

    private String selectedRoomId() { return invitation.getText().strip(); }
    private void clearSelection() { selected = null; board.selected(null); origin.setText(""); destination.setText(""); }
    private void notice(String text, boolean error) { message.setForeground(error ? new Color(155, 49, 43) : INK); message.setText(text); }
    private String subtitle(RoomView room) {
        String role = view.participant() ? "Juegas con " + (view.color().equals("White") ? "blancas" : "negras") : "Modo espectador";
        String turn = view.fen().split(" ").length > 1 && view.fen().split(" ")[1].equals("b") ? "negras" : "blancas";
        return role + " · " + (view.live() ? "Turno de " : "Posición: turno de ") + turn;
    }
    private static String status(String state) {
        return switch (state) {
            case "WAITING" -> "Esperando rival";
            case "ACTIVE" -> "Partida en juego";
            case "WHITE_WON" -> "Victoria de blancas";
            case "BLACK_WON" -> "Victoria de negras";
            default -> state;
        };
    }
    private static JPanel vertical() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        return panel;
    }
    private static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, size >= 20 || size == 12 ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }
    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        button.setForeground(primary ? Color.WHITE : TEAL);
        button.setBackground(primary ? TEAL : new Color(233, 242, 239));
        button.setFocusPainted(true);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(primary ? TEAL : new Color(211, 226, 220)), new EmptyBorder(8, 10, 8, 10)));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        return button;
    }
    private static void field(JPanel panel, JTextField field) {
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 33));
        field.setPreferredSize(new Dimension(190, 33));
        field.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(6));
        panel.add(field);
        panel.add(Box.createVerticalStrut(9));
    }
    private static String escaped(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static final class RoomRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focused) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
            RoomView room = (RoomView) value;
            label.setText("<html><b>" + escaped(room.white()) + "</b><br><span style='font-size:10px'>"
                    + escaped(status(room.status())) + " · " + room.moves().size() + " jugadas</span><br><span style='font-size:9px'>"
                    + escaped(room.id().substring(0, Math.min(18, room.id().length()))) + "…</span></html>");
            label.setBorder(new EmptyBorder(6, 8, 6, 8));
            label.setForeground(INK);
            label.setBackground(selected ? new Color(217, 235, 228) : new Color(247, 249, 249));
            return label;
        }
    }
}
