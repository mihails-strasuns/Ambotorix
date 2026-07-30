package vitbuk.com.Ambotorix.adapters.discord;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the extra messages a single logical chooser was spread across.
 *
 * <p>A chooser over 89 leaders cannot fit Discord's 25 buttons per message, so it is sent as several
 * messages. The core knows only the first one — that is the {@code MessageRef} it holds — so this maps
 * that handle onto the whole group, and remembers each message's rendered contents so an update can
 * edit <em>only</em> the messages that actually changed. Tapping one leader then costs one edit rather
 * than five.
 *
 * <p>All of this is adapter-local. Telegram puts every leader in one keyboard and needs none of it.
 */
// Only needed when the Discord adapter is configured.
@ConditionalOnProperty(name = "discord.token")
@Service
public class DiscordChooserFanout {

    /** Plenty for the lobbies one bot runs at once; oldest entries fall off rather than leak. */
    private static final int MAX_TRACKED_GROUPS = 500;

    /**
     * @param messageIds the messages making up one chooser, in display order
     * @param signatures per message, a cheap fingerprint of what is currently rendered there
     */
    public record Group(List<String> messageIds, List<List<String>> signatures) {}

    private final Map<String, Group> byFirstMessageId = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Group> eldest) {
                    return size() > MAX_TRACKED_GROUPS;
                }
            });

    public void remember(String firstMessageId, List<String> messageIds, List<List<String>> signatures) {
        if (firstMessageId == null || messageIds == null || messageIds.isEmpty()) return;
        byFirstMessageId.put(firstMessageId, new Group(List.copyOf(messageIds), List.copyOf(signatures)));
    }

    /** The group a core-held handle belongs to, or null when this message is not a spread chooser. */
    public Group of(String firstMessageId) {
        return firstMessageId == null ? null : byFirstMessageId.get(firstMessageId);
    }

    public void forget(String firstMessageId) {
        if (firstMessageId != null) byFirstMessageId.remove(firstMessageId);
    }
}
