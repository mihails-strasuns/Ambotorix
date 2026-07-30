package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance: what the renderer emits must be something Discord will actually accept.
 *
 * <p>Discord allows 5 action rows of 5 buttons, so a chooser over 89 leaders is paged: 4 rows of
 * leaders plus one row that shares page navigation with the view's own actions. The row ceiling is
 * absolute — exceed it and Discord rejects the message — so it is asserted for every shape.
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
    void largeChooserIsPagedIntoButtonRows() {
        Component.Chooser chooser = Component.Chooser.of("leaders", "Pick a leader", options(89), 3);

        List<ActionRow> rows = renderer.render(List.of(chooser));

        assertEquals(5, rows.size(), "4 rows of leaders plus a navigation row");
        rows.forEach(r -> r.getComponents().forEach(c -> assertInstanceOf(Button.class, c,
                "buttons, never a dropdown — a dropdown cannot express a ranking")));
        assertEquals(DiscordComponentRenderer.OPTIONS_PER_PAGE,
                rows.subList(0, 4).stream().mapToInt(r -> r.getComponents().size()).sum());
    }

    @Test
    void navigationSharesTheLastRowWithSubmitAndReset() {
        List<ActionRow> rows = renderer.render(List.of(
                Component.Chooser.of("leaders", "Pick", options(89), 3),
                Component.Actions.of(
                        new Component.ActionButton("✅ SUBMIT", Style.PRIMARY, new ActionRef("/hsubmit", "tok")),
                        new Component.ActionButton("🔄 RESET", Style.SECONDARY, new ActionRef("/hreset", "tok")))));

        assertEquals(5, rows.size());
        List<String> controls = labels(rows.get(4));
        assertEquals(List.of("◀", "1/5", "▶", "✅ SUBMIT", "🔄 RESET"), controls,
                "exactly five controls, which is the only way 20 leaders and both actions fit");
    }

    @Test
    void pagingShowsTheRequestedSliceAndNeverLosesOptions() {
        Component.Chooser chooser = Component.Chooser.of("leaders", "Pick", options(89), 3);
        int pages = renderer.pageCount(chooser);

        List<String> seen = new java.util.ArrayList<>();
        for (int page = 0; page < pages; page++) {
            List<ActionRow> rows = renderer.render(List.of(chooser), page);
            assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS);
            rows.subList(0, rows.size() - 1).forEach(r -> seen.addAll(labels(r)));
        }

        assertEquals(89, seen.size(), "every option must appear on exactly one page");
        assertEquals(89, seen.stream().distinct().count());
    }

    @Test
    void pagingWrapsAtBothEnds() {
        Component.Chooser chooser = Component.Chooser.of("leaders", "Pick", options(89), 3);

        // The final page is short (89 = four full pages plus nine), so the control row is simply the
        // last row rather than a fixed index.
        List<ActionRow> rows = renderer.render(List.of(chooser), 4);
        assertEquals("5/5", labels(rows.get(rows.size() - 1)).get(1));
        assertEquals(3, rows.size(), "nine leaders take two rows, plus the control row");
    }

    @Test
    void rankBadgesRideAlongOnTheButtonLabels() {
        Component.Option ranked = Component.Option.of("Lincoln", new ActionRef("/hpick", "tok", "lincoln"), "1");
        List<ActionRow> rows = renderer.render(List.of(
                new Component.Chooser("herson-pick", "Rank", List.of(ranked), 3, Selection.RANKED)));

        assertEquals(List.of("Lincoln (1)"), labels(rows.get(0)),
                "the rank has to be visible on the button itself, Telegram-style");
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

    @Test
    void theLiveHersonGridFitsWhateverTheRosterSize() {
        List<Leader> roster = leaderService.getLeaders();
        List<Component> herson = hersonPickView.grid(roster, List.of(), "abc123");

        // Paging means the roster can grow without breaking the UI, so assert the invariant that
        // actually matters: no rendered page may exceed Discord's five rows of five.
        int pages = renderer.pageCount((Component.Chooser) herson.get(0));
        for (int page = 0; page < pages; page++) {
            List<ActionRow> rows = renderer.render(herson, page);
            assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS,
                    () -> "page overflowed Discord's row limit with " + roster.size() + " leaders");
            rows.forEach(r -> assertTrue(
                    r.getComponents().size() <= DiscordComponentRenderer.MAX_BUTTONS_PER_ROW));
        }
    }

    private static List<String> labels(ActionRow row) {
        return row.getComponents().stream().map(c -> ((Button) c).getLabel()).toList();
    }
}
