package software.ulpgc.chess.contract;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class WireCodecTest {
    @Test void roundTripsUnicodeEscapesHistoryAndSession() {
        var room=new RoomView("r1","Inés = blanca","José\\negro","ACTIVE","fen",3,"2026-10-17T10:00:00Z",
                List.of(new MoveView(1,"e2","e4","","Inés = blanca","fen2","2026-10-17T10:01:00Z")));
        assertEquals(room,WireCodec.decodeRoom(WireCodec.encodeRoom(room)));
        var session=new SessionView(room,"secret","White");
        assertEquals(session,WireCodec.decodeSession(WireCodec.encodeSession(session)));
        assertEquals(List.of(room),WireCodec.decodeRooms(WireCodec.encodeRooms(List.of(room))));
        assertFalse(WireCodec.encodeRoom(room).contains("secret"));
    }
    @Test void missingFieldsAndAbusiveCountsFail() {
        assertThrows(IllegalArgumentException.class, () -> WireCodec.decodeRoom("id=x"));
        assertThrows(IllegalArgumentException.class, () -> WireCodec.decodeRooms("count=100001"));
        assertEquals(Map.of("player","Ana"),WireCodec.decodeFields("player=Ana"));
    }
}
