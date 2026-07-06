package vitbuk.com.Ambotorix.draft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import vitbuk.com.Ambotorix.PickImageGenerator;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;
import vitbuk.com.Ambotorix.services.AmbotorixService;
import vitbuk.com.Ambotorix.services.LeaderService;
import vitbuk.com.Ambotorix.services.MarkupService;

import java.io.File;
import java.util.List;

@Component
public class OpenDraftStrategy implements DraftStrategy {

    private static final Logger log = LoggerFactory.getLogger(OpenDraftStrategy.class);
    private final LeaderService leaderService;
    private final MarkupService markupService;
    private final TelegramClient telegramClient;

    public OpenDraftStrategy(LeaderService leaderService, MarkupService markupService, TelegramClient telegramClient) {
        this.leaderService = leaderService;
        this.markupService = markupService;
        this.telegramClient = telegramClient;
    }

    @Override
    public String getName() { return "open"; }

    @Override
    public void execute(Lobby lobby, Long chatId, AmbotorixService service) {
        leaderService.setLeadersPool(lobby);

        // Render pools in slot order (the drafting order), not registration order. Slot order is
        // fixed just before this in sendStart; fall back to registration order if it's somehow unset.
        List<Player> orderedPlayers = (lobby.getSlotOrder() != null && !lobby.getSlotOrder().isEmpty())
                ? lobby.getSlotOrder() : lobby.getPlayers();

        // Public group post: one combined image — a row per player — instead of a post per player.
        // It is the single draft-start ping: posted as a reply to the status message and captioned
        // with @-mentions so every player is notified. The image itself shows each player's pool.
        PickImageGenerator.LeaderPickPhoto combined =
                PickImageGenerator.createCombinedPickMessage(chatId, orderedPlayers);
        File combinedFile = combined.tempFile();
        try {
            combined.sendPhoto().setMessageThreadId(lobby.getMessageThreadId());
            combined.sendPhoto().setParseMode("HTML");
            combined.sendPhoto().setReplyToMessageId(lobby.getStatusMessageId());
            combined.sendPhoto().setCaption(service.mentionAll(lobby));
            telegramClient.execute(combined.sendPhoto());
        } catch (TelegramApiException e) {
            log.error("Failed to send combined pick image", e);
            throw new RuntimeException(e);
        } finally {
            combinedFile.delete();
        }

        // DM each reachable player their own pool with description buttons — non-fatal if it fails.
        for (Player player : orderedPlayers) {
            if (player.getUserId() == null) {
                log.warn("No userId for player {}, skipping DM", player.getUserName());
                continue;
            }
            PickImageGenerator.LeaderPickPhoto dm = PickImageGenerator.createLeaderPickMessage(player.getUserId(), player);
            File dmFile = dm.tempFile();
            try {
                dm.sendPhoto().setParseMode("HTML");
                dm.sendPhoto().setCaption("Your leaders - tap to check descriptions:");
                dm.sendPhoto().setReplyMarkup(markupService.leadersMarkup(player.getPicks()));
                telegramClient.execute(dm.sendPhoto());
            } catch (TelegramApiException e) {
                log.warn("Could not send DM to player {} (userId={}): {}", player.getUserName(), player.getUserId(), e.getMessage());
            } finally {
                dmFile.delete();
            }
        }
    }
}
