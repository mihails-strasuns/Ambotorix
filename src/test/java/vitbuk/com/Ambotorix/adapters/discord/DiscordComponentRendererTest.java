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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    void largeChooserIsSplitAcrossMessagesOfTwentyFive() {
        List<List<ActionRow>> chunks = renderer.renderChunks(
                List.of(Component.Chooser.of("leaders", "Pick a leader", options(89), 3)));

        assertEquals(4, chunks.size(), "89 options at 25 per message");
        chunks.forEach(rows -> {
            assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS);
            rows.forEach(r -> r.getComponents().forEach(c -> assertInstanceOf(Button.class, c,
                    "buttons, never a dropdown — a dropdown cannot express a ranking")));
        });
        assertEquals(89, chunks.stream().flatMap(List::stream)
                .mapToInt(r -> r.getComponents().size()).sum(),
                "every option must appear exactly once, with nothing hidden behind a control");
    }

    @Test
    void actionsLandOnTheFinalMessageBelowEveryOption() {
        List<List<ActionRow>> chunks = renderer.renderChunks(List.of(
                Component.Chooser.of("leaders", "Pick", options(89), 3),
                Component.Actions.of(
                        new Component.ActionButton("✅ SUBMIT", Style.PRIMARY, new ActionRef("/hsubmit", "tok")),
                        new Component.ActionButton("🔄 RESET", Style.SECONDARY, new ActionRef("/hreset", "tok")))));

        List<ActionRow> last = chunks.get(chunks.size() - 1);
        assertEquals(List.of("✅ SUBMIT", "🔄 RESET"), labels(last.get(last.size() - 1)),
                "Submit belongs after the whole roster, on the last message");
        // No message may exceed five rows even with the actions row appended.
        chunks.forEach(rows -> assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS,
                () -> "a message rendered " + rows.size() + " rows"));
        assertEquals(89, chunks.stream().flatMap(List::stream)
                .mapToInt(r -> r.getComponents().size()).sum() - 2);
    }

    @Test
    void aSmallChooserStillFitsOneMessage() {
        assertEquals(1, renderer.renderChunks(
                List.of(Component.Chooser.of("maps", "Pick a map", options(7), 1))).size());
    }

    @Test
    void signatureChangesOnlyForTheMessageWhoseButtonsChanged() {
        List<Component> before = List.of(Component.Chooser.of("leaders", "Pick", options(89), 3));
        List<Component.Option> after = new java.util.ArrayList<>(options(89));
        // Rank the third option, as a tap would.
        after.set(2, Component.Option.of("Option 2", new ActionRef("/x", "tok", "opt2"), "1"));

        List<List<ActionRow>> beforeChunks = renderer.renderChunks(before);
        List<List<ActionRow>> afterChunks = renderer.renderChunks(
                List.of(Component.Chooser.of("leaders", "Pick", after, 3)));

        assertNotEquals(renderer.signatureOf(beforeChunks.get(0)), renderer.signatureOf(afterChunks.get(0)));
        for (int i = 1; i < beforeChunks.size(); i++) {
            assertEquals(renderer.signatureOf(beforeChunks.get(i)), renderer.signatureOf(afterChunks.get(i)),
                    "untouched messages must compare equal, or every tap costs five edits");
        }
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

        List<List<ActionRow>> chunks = renderer.renderChunks(herson);

        // Spreading across messages means the roster can grow freely; what must hold is that no single
        // message exceeds Discord's five rows of five, and that no leader is dropped.
        chunks.forEach(rows -> {
            assertTrue(rows.size() <= DiscordComponentRenderer.MAX_ACTION_ROWS,
                    () -> "a message overflowed Discord's row limit with " + roster.size() + " leaders");
            rows.forEach(r -> assertTrue(
                    r.getComponents().size() <= DiscordComponentRenderer.MAX_BUTTONS_PER_ROW));
        });
        int buttons = chunks.stream().flatMap(List::stream).mapToInt(r -> r.getComponents().size()).sum();
        assertEquals(roster.size() + 2, buttons, "every leader, plus Submit and Reset");
    }

    private static List<String> labels(ActionRow row) {
        return row.getComponents().stream().map(c -> ((Button) c).getLabel()).toList();
    }
}
