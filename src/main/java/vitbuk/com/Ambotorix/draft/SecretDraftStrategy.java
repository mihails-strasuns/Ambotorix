package vitbuk.com.Ambotorix.draft;

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
import vitbuk.com.Ambotorix.view.PickPoolView;

@Component
public class SecretDraftStrategy implements DraftStrategy {

    private final LeaderService leaderService;
    private final PickPoolView pickPoolView;
    private final ChatGatewayRegistry chat;

    public SecretDraftStrategy(LeaderService leaderService, PickPoolView pickPoolView, ChatGatewayRegistry chat) {
        this.leaderService = leaderService;
        this.pickPoolView = pickPoolView;
        this.chat = chat;
    }

    @Override
    public String getName() { return "secret"; }

    @Override
    public void execute(Lobby lobby, ChatRef chatId, AmbotorixService service) {
        leaderService.setLeadersPool(lobby);
        String mapLine = lobby.getSelectedMap() != null
                ? "🗺 Map: " + lobby.getSelectedMap() + "\n\n"
                : "";
        for (Player player : lobby.getPlayers()) {
            boolean delivered = chat.send(OutgoingMessage.to(player.getUser())
                    .text(mapLine + "Your leaders — tap to pick one:")
                    .attachment(Attachment.png("picks.png", PickImageGenerator.renderPool(player)))
                    .component(pickPoolView.chooser(player.getPicks(), lobby.getToken()))
                    .build()).isPresent();
            if (!delivered) {
                service.sendToChat(lobby.getChat(),
                        "@" + player.getUserName() + " — couldn't send DM. Please message the bot directly first, then use <code>/pick [shortName]</code> in this chat.");
            }
        }
        // Pick pools went out as DMs; pick progress is tracked silently in the live status message.
        // Single group ping: one reply to the status message tagging everyone to check their DMs.
        service.postMilestone(chatId, service.mentionAll(lobby) + " — draft started, check your DMs to pick.");
    }

    @Override
    public void onAllPicksIn(Lobby lobby, ChatRef chatId, AmbotorixService service) {
        // The final picks are revealed in the status message; the milestone mention just pings + backlinks.
        service.refreshStatus(chatId);
        service.postMilestone(chatId, "🎉 All picks are in! See the reveal ☝️");
    }
}
