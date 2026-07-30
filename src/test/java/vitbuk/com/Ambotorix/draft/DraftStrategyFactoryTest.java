package vitbuk.com.Ambotorix.draft;

import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.services.AmbotorixService;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DraftStrategyFactoryTest {

    private DraftStrategy stub(String name) {
        return new DraftStrategy() {
            @Override public String getName() { return name; }
            @Override public void execute(Lobby lobby, ChatRef chatId, AmbotorixService service) {}
        };
    }

    @Test
    void getStrategy_returnsStrategyByName() {
        DraftStrategyFactory factory = new DraftStrategyFactory(List.of(stub("open"), stub("secret")));
        assertEquals("open", factory.getStrategy("open").getName());
        assertEquals("secret", factory.getStrategy("secret").getName());
    }

    @Test
    void getStrategy_throwsOnUnknownName() {
        DraftStrategyFactory factory = new DraftStrategyFactory(List.of(stub("open")));
        assertThrows(IllegalArgumentException.class, () -> factory.getStrategy("mystery"));
    }

    @Test
    void getStrategyNames_returnsAllRegisteredNames() {
        DraftStrategyFactory factory = new DraftStrategyFactory(List.of(stub("open"), stub("secret")));
        assertEquals(Set.of("open", "secret"), factory.getStrategyNames());
    }
}
