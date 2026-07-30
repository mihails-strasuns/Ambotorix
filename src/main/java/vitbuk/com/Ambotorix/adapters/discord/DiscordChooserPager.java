package vitbuk.com.Ambotorix.adapters.discord;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which page of a paged chooser each message is currently showing.
 *
 * <p>Paging is a Discord problem — Telegram renders all 89 leaders in one keyboard and needs none of
 * this — so it is kept entirely inside the adapter. The alternative was threading a page number
 * through {@code Component.Chooser} and the views, which would push a widget concern into
 * platform-free code and make Telegram grow pagination it does not want.
 *
 * <p>Caching the declared components (not just the options) means a ◀ ▶ tap can be answered by the
 * adapter alone, without troubling the core, while a tap that <em>does</em> change state re-renders at
 * the page the player is still looking at.
 */
// Only needed when the Discord adapter is configured.
@ConditionalOnProperty(name = "discord.token")
@Service
public class DiscordChooserPager {

    /** Plenty for the lobbies one bot runs at once; oldest entries fall off rather than leak. */
    private static final int MAX_TRACKED_MESSAGES = 500;

    private record Paged(List<Component> components, int page) {}

    private final Map<String, Paged> byMessageId = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Paged> eldest) {
                    return size() > MAX_TRACKED_MESSAGES;
                }
            });

    /** Record what a message is displaying, so later edits and page taps can reproduce it. */
    public void remember(String messageId, List<Component> components, int page) {
        if (messageId == null) return;
        byMessageId.put(messageId, new Paged(components, page));
    }

    public int pageOf(String messageId) {
        Paged paged = messageId == null ? null : byMessageId.get(messageId);
        return paged == null ? 0 : paged.page();
    }

    /** The components a message was rendered from, or null if it is not one we track. */
    public List<Component> componentsOf(String messageId) {
        Paged paged = messageId == null ? null : byMessageId.get(messageId);
        return paged == null ? null : paged.components();
    }

    public void forget(String messageId) {
        if (messageId != null) byMessageId.remove(messageId);
    }
}
