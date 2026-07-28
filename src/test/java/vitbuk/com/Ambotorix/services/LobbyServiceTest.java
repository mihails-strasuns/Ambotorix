package vitbuk.com.Ambotorix.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;

import static org.junit.jupiter.api.Assertions.*;

class LobbyServiceTest {

    /** Lobbies are addressed by a platform-qualified channel, not a bare id. */
    private static ChatRef chat(long chatId) {
        return ChatRef.telegram(chatId, null);
    }


    private LobbyService service;
    private static final ChatRef CHAT_A = chat(100L);
    private static final ChatRef CHAT_B = chat(200L);

    @BeforeEach
    void setUp() {
        service = new LobbyService();
    }

    @Test
    void createLobby_createsLobbyForChat() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        assertTrue(service.hasLobby(CHAT_A));
    }

    @Test
    void twoDifferentChatsHaveIndependentLobbies() {
        service.createLobby(CHAT_A, new Player("hostA", 1L));
        service.createLobby(CHAT_B, new Player("hostB", 2L));

        assertEquals("hostA", service.getLobby(CHAT_A).getHost().getUserName());
        assertEquals("hostB", service.getLobby(CHAT_B).getHost().getUserName());
    }

    @Test
    void createLobby_whenAlreadyExists_returnsAlreadyExistsMessage() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        String result = service.createLobby(CHAT_A, new Player("other", 2L));
        assertTrue(result.toLowerCase().contains("already"));
    }

    @Test
    void removeLobby_deletesLobby() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        service.removeLobby(CHAT_A);
        assertFalse(service.hasLobby(CHAT_A));
    }

    @Test
    void registerPlayer_addsPlayerToLobby() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        service.registerPlayer(CHAT_A, "player2", 2L);
        assertTrue(service.isRegistered(CHAT_A, "player2"));
    }

    @Test
    void isHost_returnsTrueForHost_falseForOthers() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        assertTrue(service.isHost(CHAT_A, "host"));
        assertFalse(service.isHost(CHAT_A, "nothost"));
    }

    @Test
    void isHost_returnsFalse_whenNoLobby() {
        assertFalse(service.isHost(CHAT_A, "anyone"));
    }

    @Test
    void addMap_appendsToMapPool() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        service.addMap(CHAT_A, CivMap.FRACTAL);
        assertTrue(service.getMappool(CHAT_A).contains(CivMap.FRACTAL));
    }

    @Test
    void removeMap_returnsFalse_whenMapNotInPool() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        assertFalse(service.removeMap(CHAT_A, CivMap.FRACTAL));
    }

    @Test
    void getExpiredLobbyChats_returnsChatsWhoseStartExceededTimeout() {
        service.createLobby(CHAT_A, new Player("host", 1L));
        Lobby lobby = service.getLobby(CHAT_A);
        lobby.setDraftStartedAt(java.time.LocalDateTime.now().minusMinutes(45));

        var expired = service.getExpiredLobbyChats(30);
        assertTrue(expired.contains(CHAT_A));
    }
}
