package vitbuk.com.Ambotorix.chat;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Routes an outgoing message to the gateway of its platform, so core code holds one collaborator
 * instead of one per platform. Which gateways exist is a deployment question — a Telegram-only
 * deployment simply has no Discord bean.
 */
@Component
public class ChatGatewayRegistry {

    private final Map<Platform, ChatGateway> gateways = new EnumMap<>(Platform.class);

    public ChatGatewayRegistry(List<ChatGateway> gateways) {
        for (ChatGateway gateway : gateways) {
            ChatGateway previous = this.gateways.put(gateway.platform(), gateway);
            if (previous != null) {
                throw new IllegalStateException("Two gateways registered for " + gateway.platform());
            }
        }
        if (this.gateways.isEmpty()) {
            // Both adapters are optional, so "none configured" is reachable by omission — and a bot
            // that starts cleanly but can never speak to anyone is the worst outcome. Say so loudly.
            throw new IllegalStateException("No chat platform is configured, so the bot has nowhere to "
                    + "talk. Set bot.token for Telegram, discord.token for Discord, or both.");
        }
    }

    public ChatGateway of(Platform platform) {
        ChatGateway gateway = gateways.get(platform);
        if (gateway == null) throw new IllegalStateException("No chat gateway for platform " + platform);
        return gateway;
    }

    public ChatGateway of(ChatRef chat) { return of(chat.platform()); }

    public ChatGateway of(Audience audience) { return of(audience.platform()); }

    /** Deliver a message to whichever platform its audience lives on. */
    public Optional<MessageRef> send(OutgoingMessage message) {
        return of(message.to()).send(message);
    }

    public boolean editText(MessageRef ref, String text, MentionTable mentions) {
        return of(ref.chat()).editText(ref, text, mentions);
    }

    public boolean editComponents(MessageRef ref, List<vitbuk.com.Ambotorix.chat.ui.Component> components) {
        return of(ref.chat()).editComponents(ref, components);
    }
}
