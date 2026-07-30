package vitbuk.com.Ambotorix.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.UserRef;
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
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        assertTrue(service.hasLobby(CHAT_A));
    }

    @Test
    void twoDifferentChatsHaveIndependentLobbies() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "hostA")));
        service.createLobby(CHAT_B, new Player(UserRef.telegram(2L, "hostB")));

        assertEquals("hostA", service.getLobby(CHAT_A).getHost().getUserName());
        assertEquals("hostB", service.getLobby(CHAT_B).getHost().getUserName());
    }

    @Test
    void createLobby_whenAlreadyExists_returnsAlreadyExistsMessage() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        String result = service.createLobby(CHAT_A, new Player(UserRef.telegram(2L, "other")));
        assertTrue(result.toLowerCase().contains("already"));
    }

    @Test
    void removeLobby_deletesLobby() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        service.removeLobby(CHAT_A);
        assertFalse(service.hasLobby(CHAT_A));
    }

    @Test
    void registerPlayer_addsPlayerToLobby() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        service.registerPlayer(CHAT_A, UserRef.telegram(2L, "player2"));
        assertTrue(service.isRegistered(CHAT_A, "player2"));
    }

    @Test
    void isHost_returnsTrueForHost_falseForOthers() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        assertTrue(service.isHost(CHAT_A, "host"));
        assertFalse(service.isHost(CHAT_A, "nothost"));
    }

    @Test
    void isHost_returnsFalse_whenNoLobby() {
        assertFalse(service.isHost(CHAT_A, "anyone"));
    }

    @Test
    void addMap_appendsToMapPool() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        service.addMap(CHAT_A, CivMap.FRACTAL);
        assertTrue(service.getMappool(CHAT_A).contains(CivMap.FRACTAL));
    }

    @Test
    void removeMap_returnsFalse_whenMapNotInPool() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        assertFalse(service.removeMap(CHAT_A, CivMap.FRACTAL));
    }

    @Test
    void getExpiredLobbyChats_returnsChatsWhoseStartExceededTimeout() {
        service.createLobby(CHAT_A, new Player(UserRef.telegram(1L, "host")));
        Lobby lobby = service.getLobby(CHAT_A);
        lobby.setDraftStartedAt(java.time.LocalDateTime.now().minusMinutes(45));

        var expired = service.getExpiredLobbyChats(30);
        assertTrue(expired.contains(CHAT_A));
    }
}
