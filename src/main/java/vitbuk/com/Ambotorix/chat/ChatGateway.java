package vitbuk.com.Ambotorix.chat;

import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.List;
import java.util.Optional;

/**
 * Everything the bot can do <em>to</em> a chat platform. One implementation per platform, and the
 * only place platform SDK types are allowed to appear.
 *
 * <p>Deliberately synchronous: today every send completes before the inbound handler returns, and
 * the scenario suite depends on that. Adapters over async SDKs block (MULTIPLATFORM_PLAN.md D3).
 *
 * <p>Inbound acknowledgement (Telegram's {@code answerCallbackQuery}, Discord's {@code deferEdit})
 * is not on this interface — an adapter acks as part of mapping the event, before the bot ever sees
 * it, so no core code has to remember to.
 */
public interface ChatGateway {

    Platform platform();

    /**
     * Deliver a message. Empty means the platform refused — almost always an unreachable DM (the
     * user has never opened a chat with the bot, or has DMs disabled), which callers handle by
     * falling back to the group.
     */
    Optional<MessageRef> send(OutgoingMessage message);

    /** Re-render a message's text in place. False if the message is gone or the edit was rejected. */
    boolean editText(MessageRef ref, String text, MentionTable mentions);

    /** Re-render a message's interactive components in place, leaving its text alone. */
    boolean editComponents(MessageRef ref, List<Component> components);

    Capabilities capabilities();

    /** Platform limits a view may need to respect. Rendering itself never leaks out of the adapter. */
    record Capabilities(int maxTextLength, int maxCaptionLength, int maxOptionsPerMessage) {}
}
