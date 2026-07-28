package vitbuk.com.Ambotorix.draft;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.services.AmbotorixService;

/**
 * Ranked secret draft. Each player DMs four ranked picks; the bot resolves clashes by banning
 * contested civs and falling players through to their next pick, breaking any clash that survives all
 * four picks with a coin flip. Picks stay hidden until the whole draft is resolved.
 *
 * <p>Unlike {@code open}/{@code secret}, picks arrive as free-text DMs (not {@code /pick}); the
 * dispatcher routes those to {@link AmbotorixService#handleDirectMessage}. All the real logic lives on
 * {@link AmbotorixService}; this strategy is just the kickoff hook.
 */
@Component
public class HersonDraftStrategy implements DraftStrategy {

    @Override
    public String getName() { return "herson"; }

    @Override
    public void execute(Lobby lobby, ChatRef chatId, AmbotorixService service) {
        service.sendHersonStart(lobby, chatId);
    }
}
