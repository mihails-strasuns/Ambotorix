package vitbuk.com.Ambotorix.draft;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.services.AmbotorixService;

/**
 * Stricter variant of {@link HersonDraftStrategy}: any civ that two or more players ranked is banned
 * outright — <em>regardless of the priority</em> each gave it — so every assigned civ was wanted by
 * just one player. Each player then keeps their highest-ranked surviving pick; anyone left with no
 * surviving pick re-picks from the remaining pool. There is no same-priority-only nuance and no coin
 * flip.
 *
 * <p>Shares the entire DM submission flow with {@code herson}; only the resolution rule differs
 * (selected by strategy name in {@link AmbotorixService#advanceHersonResolution}).
 */
@Component
public class HersonLowDraftStrategy implements DraftStrategy {

    @Override
    public String getName() { return "herson-low"; }

    @Override
    public void execute(Lobby lobby, ChatRef chatId, AmbotorixService service) {
        service.sendHersonStart(lobby, chatId);
    }
}
