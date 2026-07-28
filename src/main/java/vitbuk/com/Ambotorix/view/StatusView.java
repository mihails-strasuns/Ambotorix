package vitbuk.com.Ambotorix.view;

import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.entities.HersonDraftState;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Lobby;
import vitbuk.com.Ambotorix.entities.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the lobby's live status message — all metadata plus draft progress — kept in the one
 * message the bot edits for the whole session.
 *
 * <p>Pure: domain in, text out, no sending and no platform types. {@code @username} is a mention
 * placeholder the chat adapter rewrites for its platform (Telegram leaves it, Discord turns it into
 * {@code <@id>}); the markup is the HTML subset every adapter knows how to render.
 */
public final class StatusView {

    private StatusView() {}

    public static String render(Lobby lobby) {
        String playerList = lobby.getPlayers().stream()
                .map(Player::getUserName)
                .collect(Collectors.joining(", "));
        String draftStatus = !lobby.isDraftStarted() ? "waiting"
                : (lobby.isDraftInProgress() ? "drafting" : "done");
        boolean herson = lobby.isHersonDraft();

        StringBuilder sb = new StringBuilder();
        sb.append("🎲 <b>Lobby by @").append(lobby.getHost().getUserName()).append("</b>\n")
                .append("Status: ").append(draftStatus);

        // Every tunable parameter, kept current as the host defines it. Herson resolves its own bans
        // and submits a fixed 4 ranked picks, so the manual ban/pick-size knobs don't apply to it.
        sb.append("\n\n<b>Settings:</b>")
                .append("\nDraft: ").append(lobby.getDraftStrategyName());
        if (!herson) {
            sb.append("\nPick size: ").append(lobby.getPickSize())
                    .append("\nBans per player: ").append(lobby.getBanSize());
        }
        // The map is rolled up front (at lobby creation), so surface it from the start. During setup
        // also show the pool it was drawn from, since the host can still add/remove maps.
        sb.append("\nMap: ").append(lobby.getSelectedMap() == null ? "—" : lobby.getSelectedMap().toString());
        if (!lobby.isDraftStarted()) {
            String mapList = lobby.getMapPool().isEmpty() ? "none"
                    : lobby.getMapPool().stream().map(CivMap::toString).collect(Collectors.joining(", "));
            sb.append("\nMap pool: ").append(mapList);
        }

        sb.append("\n\n<b>Players (").append(lobby.getPlayers().size()).append("):</b> ").append(playerList);

        if (!herson && lobby.getBanSize() > 0) {
            sb.append("\n\n<b>Bans:</b>");
            for (Player player : lobby.getPlayers()) {
                sb.append("\n@").append(player.getUserName()).append(": ");
                if (player.getBans().isEmpty()) {
                    sb.append("—");
                } else {
                    sb.append(player.getBans().stream()
                            .map(Leader::getFullName)
                            .collect(Collectors.joining(", ")));
                }
            }
        }

        // Herson has no per-player ban phase, but the host can remove civs from the pool at any time
        // before the draft closes — surface those so players know what's off the table.
        if (herson && !lobby.getBannedLeaders().isEmpty()) {
            sb.append("\n\n<b>Host bans:</b> ").append(lobby.getBannedLeaders().stream()
                    .map(Leader::getFullName)
                    .collect(Collectors.joining(", ")));
        }

        if (lobby.isDraftStarted() && lobby.getSlotOrder() != null && !lobby.getSlotOrder().isEmpty()) {
            sb.append("\n\n<b>Slot order:</b>");
            List<Player> order = lobby.getSlotOrder();
            for (int i = 0; i < order.size(); i++) {
                sb.append("\n").append(i + 1).append(". ").append(order.get(i).getUserName());
            }
        }

        // Hidden-draft pick progress / reveal.
        if ("secret".equals(lobby.getDraftStrategyName()) && lobby.isDraftInProgress()) {
            sb.append("\n\n<b>Picks:</b> ").append(lobby.getPendingPicks().size())
                    .append("/").append(lobby.getPlayers().size()).append(" in");
        } else if (herson && lobby.isDraftInProgress()) {
            int submitted = lobby.getHersonState() == null ? 0 : lobby.getHersonState().getRankedPicks().size();
            sb.append("\n\n<b>Submissions:</b> ").append(submitted)
                    .append("/").append(lobby.getPlayers().size()).append(" in");
            if (lobby.getHersonState() != null && lobby.getHersonState().anyAwaitingRepick()) {
                sb.append("\nResolving — awaiting a coin-flip re-pick…");
            }
        } else if (!herson && !lobby.getPendingPicks().isEmpty()) {
            // Herson reveals picks as a portrait image, not in the status.
            sb.append("\n\n<b>Picks:</b>");
            for (Map.Entry<String, Leader> e : lobby.getPendingPicks().entrySet()) {
                sb.append("\n@").append(e.getKey()).append(" → ").append(e.getValue().getFullName());
            }
        }

        // Once a Herson draft has closed, explain the resolution: which civs were contested (banned)
        // and which players had ranked them (and at what priority), so the outcome is transparent.
        if (herson && lobby.isDraftStarted() && !lobby.isDraftInProgress()
                && lobby.getHersonState() != null && !lobby.getHersonState().getBanned().isEmpty()) {
            HersonDraftState state = lobby.getHersonState();
            sb.append("\n\n<b>Contested (banned):</b>");
            for (Leader civ : state.getBanned()) {
                List<String> who = new ArrayList<>();
                for (Map.Entry<String, List<Leader>> e : state.getRankedPicks().entrySet()) {
                    int idx = e.getValue().indexOf(civ);
                    if (idx >= 0) who.add("@" + e.getKey() + " (#" + (idx + 1) + ")");
                }
                sb.append("\n").append(civ.getFullName());
                if (!who.isEmpty()) sb.append(" — ").append(String.join(", ", who));
            }
        }

        return sb.toString();
    }
}
