package software.ulpgc.chess.frontend.adapters.swing;

import software.ulpgc.chess.contract.RoomView;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Filters the room catalogue using the criteria of the desktop view. */
record RoomFilter(String query, Status status) {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    RoomFilter {
        query = normalize(query.strip());
    }

    boolean matches(RoomView room) {
        if (!status.matches(room.status())) return false;
        if (query.isEmpty()) return true;
        return normalize(room.id()).contains(query)
                || normalize(room.white()).contains(query)
                || normalize(room.black()).contains(query);
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("");
    }

    enum Status {
        ALL("Todos los estados"),
        WAITING("Esperando rival", "WAITING"),
        ACTIVE("En juego", "ACTIVE"),
        FINISHED("Terminadas", "WHITE_WON", "BLACK_WON");

        private final String label;
        private final String[] codes;

        Status(String label, String... codes) {
            this.label = label;
            this.codes = codes;
        }

        boolean matches(String status) {
            if (codes.length == 0) return true;
            for (String code : codes) {
                if (code.equals(status)) return true;
            }
            return false;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
