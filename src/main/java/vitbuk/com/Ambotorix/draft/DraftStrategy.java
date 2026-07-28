package vitbuk.com.Ambotorix.draft;

import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.services.AmbotorixService;

public interface DraftStrategy {
    String getName();
    void execute(Lobby lobby, ChatRef chatId, AmbotorixService service);
    default void onAllPicksIn(Lobby lobby, ChatRef chatId, AmbotorixService service) {}
}
