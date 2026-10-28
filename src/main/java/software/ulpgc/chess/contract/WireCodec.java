package software.ulpgc.chess.contract;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/** Unico lugar que conoce el formato de transporte Properties UTF-8 v1. */
public final class WireCodec {
    private WireCodec() {}
    public static String encodeFields(Map<String, String> fields) {
        Properties properties = new Properties();
        properties.putAll(fields);
        StringWriter writer = new StringWriter();
        try { properties.store(writer, "chess-software v1"); }
        catch (IOException ex) { throw new IllegalStateException(ex); }
        return writer.toString();
    }
    public static Map<String, String> decodeFields(String text) {
        Properties properties = new Properties();
        try { properties.load(new StringReader(text)); }
        catch (IOException | IllegalArgumentException ex) { throw new IllegalArgumentException("Mensaje Properties invalido", ex); }
        Map<String, String> result = new HashMap<>();
        properties.stringPropertyNames().forEach(key -> result.put(key, properties.getProperty(key)));
        return Map.copyOf(result);
    }
    public static String encodeRoom(RoomView room) { Map<String,String> fields = new LinkedHashMap<>(); putRoom(fields, "", room); return encodeFields(fields); }
    public static RoomView decodeRoom(String text) { return room(decodeFields(text), ""); }
    public static String encodeSession(SessionView session) {
        Map<String,String> fields = new LinkedHashMap<>(); putRoom(fields, "", session.room());
        fields.put("token", session.token()); fields.put("color", session.color()); return encodeFields(fields);
    }
    public static SessionView decodeSession(String text) {
        Map<String,String> fields = decodeFields(text);
        return new SessionView(room(fields, ""), required(fields, "token"), required(fields, "color"));
    }
    public static String encodeRooms(List<RoomView> rooms) {
        Map<String,String> fields = new LinkedHashMap<>(); fields.put("count", "" + rooms.size());
        for (int i = 0; i < rooms.size(); i++) putRoom(fields, "room." + i + ".", rooms.get(i));
        return encodeFields(fields);
    }
    public static List<RoomView> decodeRooms(String text) {
        Map<String,String> fields = decodeFields(text); List<RoomView> result = new ArrayList<>();
        int count = count(fields, "count");
        for (int i = 0; i < count; i++) result.add(room(fields, "room." + i + "."));
        return List.copyOf(result);
    }
    private static void putRoom(Map<String,String> f, String p, RoomView r) {
        f.put(p + "id", r.id()); f.put(p + "white", r.white()); f.put(p + "black", r.black());
        f.put(p + "status", r.status()); f.put(p + "fen", r.fen()); f.put(p + "version", "" + r.version());
        f.put(p + "createdAt", r.createdAt()); f.put(p + "moves.count", "" + r.moves().size());
        for (int i = 0; i < r.moves().size(); i++) {
            MoveView m = r.moves().get(i); String k = p + "move." + i + ".";
            f.put(k + "number", "" + m.number()); f.put(k + "from", m.from()); f.put(k + "to", m.to());
            f.put(k + "promotion", m.promotion()); f.put(k + "player", m.player()); f.put(k + "fen", m.fen()); f.put(k + "at", m.at());
        }
    }
    private static RoomView room(Map<String,String> f, String p) {
        List<MoveView> moves = new ArrayList<>(); int size = count(f, p + "moves.count");
        for (int i = 0; i < size; i++) {
            String k = p + "move." + i + ".";
            moves.add(new MoveView(count(f, k + "number"), required(f,k+"from"), required(f,k+"to"),
                    required(f,k+"promotion"),required(f,k+"player"),required(f,k+"fen"),required(f,k+"at")));
        }
        try {
            return new RoomView(required(f,p+"id"),required(f,p+"white"),required(f,p+"black"),required(f,p+"status"),
                    required(f,p+"fen"),Long.parseLong(required(f,p+"version")),required(f,p+"createdAt"),moves);
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Version invalida", ex); }
    }
    private static int count(Map<String,String> f, String key) {
        try {
            int value = Integer.parseInt(required(f,key));
            if (value < 0 || value > 100000) throw new IllegalArgumentException("Cantidad fuera de limite");
            return value;
        } catch (NumberFormatException ex) { throw new IllegalArgumentException("Cantidad invalida: " + key, ex); }
    }
    private static String required(Map<String,String> f, String key) {
        String value = f.get(key);
        if (value == null) throw new IllegalArgumentException("Falta campo: " + key);
        return value;
    }
}
