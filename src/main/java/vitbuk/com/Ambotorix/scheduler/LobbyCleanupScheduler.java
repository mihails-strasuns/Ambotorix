package vitbuk.com.Ambotorix.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.services.AmbotorixService;
import vitbuk.com.Ambotorix.services.LobbyService;

import java.util.List;

@Component
public class LobbyCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(LobbyCleanupScheduler.class);

    private final LobbyService lobbyService;
    private final AmbotorixService ambotorixService;

    @Value("${lobby.auto-terminate.minutes:30}")
    private int autoTerminateMinutes;

    public LobbyCleanupScheduler(LobbyService lobbyService, AmbotorixService ambotorixService) {
        this.lobbyService = lobbyService;
        this.ambotorixService = ambotorixService;
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void terminateExpiredLobbies() {
        List<ChatRef> expired = lobbyService.getExpiredLobbyChats(autoTerminateMinutes);
        for (ChatRef chatId : expired) {
            log.info("Auto-terminating lobby in chat {} ({}m after /start)", chatId, autoTerminateMinutes);
            ChatRef lobbyChat = lobbyService.getLobby(chatId).getChat();
            lobbyService.removeLobby(chatId);
            ambotorixService.sendToChat(lobbyChat,
                "Lobby automatically terminated after " + autoTerminateMinutes + " minutes.");
        }
    }
}
