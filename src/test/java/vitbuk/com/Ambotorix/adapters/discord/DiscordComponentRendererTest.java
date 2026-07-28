package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.chat.ui.Selection;
import vitbuk.com.Ambotorix.chat.ui.Style;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.services.LeaderService;
import vitbuk.com.Ambotorix.view.HersonPickView;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance: what the renderer emits must be something Discord will actually accept.
 *
 * <p>The load-bearing one is {@link #liveRosterStillFitsDiscordsFiveRowCeiling()}. Discord allows 5
 * action rows per message and 25 options per select menu, so the whole roster plus a Submit/Reset row
 * has to fit in 4 menus — 100 option slots. Today's roster is 89. When BBG grows past 100 this test
 * fails, which is the point: better a red build than an exception mid-draft.
 */
@SpringBootTest
class DiscordComponentRendererTest {

    private final DiscordComponentRenderer renderer = new DiscordComponentRenderer();

    @Autowired LeaderService leaderService;
    @Autowired HersonPickView hersonPickView;

    private static List<Component.Option> options(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Component.Option.of("Option " + i, new ActionRef("/x", "tok", "opt" + i)))
                .toList();
    }

    @Test
    void smallChooserBecomesButtons() {
        Component.Chooser chooser = Component.Chooser.of("maps", "Pick a map", options(7), 1);

        List<ActionRow> rows = renderer.render(List.of(chooser));

        assertEquals(2, rows.size(), "7 options at 5 per row");
        assertTrue(rows.get(0).getComponents().get(0) instanceof Button);
    }

    @Test
    void largeChooserBecomesSelectMenus() {
        Component.Chooser chooser = Component.Chooser.of("leaders", "Pick a leader", options(60), 3);

        List<ActionRow> rows = renderer.render(List.of(chooser));

        assertEquals(3, rows.size(), "60 options at 25 per menu");
        rows.forEach(r -> assertTrue(r.getComponents().get(0) instanceof StringSelectMenu));
    }

    @Test
    void rankedChooserUsesSelectMenusEvenWhenSmall() {
        // Ranking needs per-option state (the rank badge), which buttons cannot carry on Discord.
        Component.Chooser chooser = new Component.Chooser("herson-pick", "Rank", options(10), 3, Selection.RANKED);

        List<ActionRow> rows = renderer.render(List.of(chooser));

        assertTrue(rows.get(0).getComponents().get(0) instanceof StringSelectMenu);
    }

    @Test
    void actionsLandOnTheLastRow() {
        Component.Chooser chooser = Component.Chooser.of("leaders", "Pick", options(60), 3);
        Component.Actions actions = Component.Actions.of(
                new Component.ActionButton("✅ SUBMIT", Style.PRIMARY, new ActionRef("/hsubmit", "tok")));

        List<ActionRow> rows = renderer.render(List.of(chooser, actions));

        assertTrue(rows.get(rows.size() - 1).getComponents().get(0) instanceof Button,
                "Submit must be reachable, below the menus");
    }

    @Test
    void neverExceedsFiveActionRows() {
        Component.Chooser huge = Component.Chooser.of("leaders", "Pick", options(500), 3);
        Component.Actions actions = Component.Actions.of(
                new Component.ActionButton("✅ SUBMIT", Style.PRIMARY, new ActionRef("/hsubmit", "tok")));

        List<ActionRow> rows = renderer.render(List.of(huge, actions));

        assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS, "got " + rows.size() + " rows");
        assertTrue(rows.get(rows.size() - 1).getComponents().get(0) instanceof Button,
                "actions are kept even when the chooser has to be truncated");
    }

    @Test
    void liveRosterStillFitsDiscordsFiveRowCeiling() {
        List<Leader> roster = leaderService.getLeaders();
        List<Component> herson = hersonPickView.grid(roster, List.of(), "abc123");

        List<ActionRow> rows = renderer.render(herson);

        int capacity = (DiscordComponentRenderer.MAX_ACTION_ROWS - 1) * DiscordComponentRenderer.MAX_OPTIONS_PER_MENU;
        assertTrue(roster.size() <= capacity,
                () -> "The roster has grown to " + roster.size() + " leaders but only " + capacity
                        + " fit in Discord's 4 usable select menus. Add letter-filter paging to "
                        + "DiscordComponentRenderer before shipping this roster.");
        assertEquals(Math.ceil(roster.size() / 25.0) + 1, (double) rows.size(),
                "one menu per 25 leaders, plus the Submit/Reset row");
    }

    @Test
    void payloadsStayInsideBothPlatformsLimits() {
        List<Component> herson = hersonPickView.grid(leaderService.getLeaders(), List.of(), "abc123");

        for (Component component : herson) {
            List<String> payloads = switch (component) {
                case Component.Chooser c -> c.options().stream().map(o -> o.action().encode()).toList();
                case Component.Actions a -> a.buttons().stream().map(b -> b.action().encode()).toList();
            };
            for (String payload : payloads) {
                // Telegram's callback_data is the tighter of the two budgets at 64 bytes.
                assertTrue(payload.getBytes().length <= 64,
                        () -> "payload too long for Telegram callback_data: " + payload);
                assertTrue(payload.length() <= 100,
                        () -> "payload too long for a Discord custom_id: " + payload);
            }
        }
    }
}
