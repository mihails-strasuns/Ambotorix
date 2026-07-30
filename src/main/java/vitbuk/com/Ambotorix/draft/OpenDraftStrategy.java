package vitbuk.com.Ambotorix.draft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.PickImageGenerator;
import vitbuk.com.Ambotorix.chat.Attachment;
import vitbuk.com.Ambotorix.chat.ChatGatewayRegistry;
import vitbuk.com.Ambotorix.chat.OutgoingMessage;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;
import vitbuk.com.Ambotorix.services.AmbotorixService;
import vitbuk.com.Ambotorix.services.LeaderService;
import vitbuk.com.Ambotorix.view.LeaderGridView;

import java.util.List;

@Component
public class OpenDraftStrategy implements DraftStrategy {

    private static final Logger log = LoggerFactory.getLogger(OpenDraftStrategy.class);
    private final LeaderService leaderService;
    private final LeaderGridView leaderGridView;
    private final ChatGatewayRegistry chat;

    public OpenDraftStrategy(LeaderService leaderService, LeaderGridView leaderGridView, ChatGatewayRegistry chat) {
        this.leaderService = leaderService;
        this.leaderGridView = leaderGridView;
        this.chat = chat;
    }

    @Override
    public String getName() { return "open"; }

    @Override
    public void execute(Lobby lobby, ChatRef chatId, AmbotorixService service) {
        leaderService.setLeadersPool(lobby);

        // Render pools in slot order (the drafting order), not registration order. Slot order is
        // fixed just before this in sendStart; fall back to registration order if it's somehow unset.
        List<Player> orderedPlayers = (lobby.getSlotOrder() != null && !lobby.getSlotOrder().isEmpty())
                ? lobby.getSlotOrder() : lobby.getPlayers();

        // Public group post: one combined image — a row per player — instead of a post per player.
        // It is the single draft-start ping: posted as a reply to the status message and captioned
        // with @-mentions so every player is notified. The image itself shows each player's pool.
        boolean posted = chat.send(OutgoingMessage.to(lobby.getChat())
                .text(service.mentionAll(lobby))
                .mentions(service.mentionsOf(lobby))
                .attachment(Attachment.png("picks.png", PickImageGenerator.renderPools(orderedPlayers)))
                .replyTo(lobby.getStatusMessage())
                .build()).isPresent();
        if (!posted) {
            // The pools are the whole point of an open draft — if the group post failed there is
            // nothing to fall back to, so let sendStart roll the draft back.
            throw new IllegalStateException("Could not post the combined pick image to " + lobby.getChat());
        }

        // DM each reachable player their own pool with description buttons — non-fatal if it fails.
        for (Player player : orderedPlayers) {
            boolean delivered = chat.send(OutgoingMessage.to(player.getUser())
                    .text("Your leaders - tap to check descriptions:")
                    .attachment(Attachment.png("picks.png", PickImageGenerator.renderPool(player)))
                    .component(leaderGridView.list(player.getPicks()))
                    .build()).isPresent();
            if (!delivered) {
                log.warn("Could not DM pick pool to player {}", player.getUserName());
            }
        }
    }
}
