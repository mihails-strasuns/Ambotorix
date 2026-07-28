package vitbuk.com.Ambotorix.scenario;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import vitbuk.com.Ambotorix.adapters.telegram.TelegramBot;
import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.harness.TestTelegramClient;
import vitbuk.com.Ambotorix.harness.TestTelegramClient.Button;
import vitbuk.com.Ambotorix.harness.TestTelegramClient.OutboundMessage;
import vitbuk.com.Ambotorix.scenario.LineMatcher.MatchContext;
import vitbuk.com.Ambotorix.services.LeaderService;
import vitbuk.com.Ambotorix.services.LobbyService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Runs every {@code src/test/resources/scenarios/*.chat} file as a dynamic test against the real
 * Spring wiring, with {@link TestTelegramClient} standing in for Telegram. See
 * SCENARIO_TESTING_PLAN.md.
 *
 * <p>Output matching is "pseudo-consuming": per chat the bot's history is a single append-only list,
 * and a per-chat {@link #cursors cursor} marks how far expectations have advanced. Each expectation
 * scans forward from the cursor (subsequence — intervening, unasserted output is skipped);
 * {@code <press_button>} scans the whole list and never moves the cursor.
 */
@SpringBootTest
@Import(ScenarioRunnerTest.TestBeans.class)
class ScenarioRunnerTest {

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        TestTelegramClient testTelegramClient() {
            return new TestTelegramClient();
        }

        // Canned photo-challenge sheet so /photochallenge scenarios run offline.
        @Bean
        @Primary
        vitbuk.com.Ambotorix.photochallenge.SheetCsvFetcher testSheetCsvFetcher() {
            return () -> """
                    Player,Total,Leaders,City-States,Wonders
                    Shu B,4,1,2,1
                    Misha B,4,1,2,1
                    Vitalik B,1,1,0,0
                    """;
        }
    }

    private static final Path SCENARIO_DIR = Path.of("src/test/resources/scenarios");

    @Autowired TelegramBot ambotorix;
    @Autowired TestTelegramClient client;
    @Autowired LobbyService lobbyService;
    @Autowired LeaderService leaderService;

    /** A chat plus the forum topic within it — the unit output is matched against, in order. */
    private record Channel(long chatId, Integer threadId) {}

    /** Per-channel read position into the bot's history; reset per scenario. */
    private Map<Channel, Integer> cursors = new HashMap<>();

    @TestFactory
    Stream<DynamicTest> scenarios() throws IOException {
        try (Stream<Path> files = Files.list(SCENARIO_DIR)) {
            List<Path> chatFiles = files.filter(p -> p.toString().endsWith(".chat")).sorted().toList();
            return chatFiles.stream().map(this::toDynamicTest).toList().stream();
        }
    }

    private DynamicTest toDynamicTest(Path file) {
        Scenario scenario = Scenario.parse(read(file));
        return DynamicTest.dynamicTest(scenario.name() + " [" + file.getFileName() + "]", () -> run(scenario));
    }

    private void run(Scenario scenario) {
        resetState();
        client.bindBot(ambotorix::consume);

        switch (scenario.disposition()) {
            case DISABLED -> Assumptions.abort("scenario marked DISABLED");
            case RUN -> executeSteps(scenario);
            case XFAIL -> {
                try {
                    executeSteps(scenario);
                } catch (Throwable expected) {
                    Assumptions.abort("XFAIL as expected: " + expected.getMessage());
                    return;
                }
                Assertions.fail("XFAIL scenario unexpectedly passed — implement is complete, remove the marker");
            }
        }
    }

    private void executeSteps(Scenario scenario) {
        MatchContext ctx = new MatchContext(scenario.actorNames(), leaderNames());
        Set<Channel> assertedChannels = new HashSet<>();
        for (Scenario.Step step : scenario.steps()) {
            switch (step) {
                case Scenario.Step.Say say ->
                        client.deliverMessage(say.from().name(), say.from().userId(), say.chatId(), say.threadId(), say.text());
                case Scenario.Step.Tap tap -> executeTap(tap, ctx);
                case Scenario.Step.ExpectMessage e -> { assertedChannels.add(channel(e)); expectMessage(e, ctx); }
                case Scenario.Step.ExpectDrain e -> { assertedChannels.add(channel(e)); expectDrain(e); }
                case Scenario.Step.ExpectStatus e -> { assertedChannels.add(channel(e)); expectStatus(e, ctx); }
            }
        }
        // Completeness: a channel we asserted anything about must be fully accounted for — no surprises left.
        for (Channel ch : assertedChannels) {
            Assertions.assertTrue(cursor(ch) >= client.history(ch.chatId(), ch.threadId()).size(),
                    () -> "Unexpected trailing output in " + ch + ":" + renderFrom(ch));
        }
    }

    /** Strict: the next unconsumed message in the channel must satisfy every predicate, else fail. */
    private void expectMessage(Scenario.Step.ExpectMessage e, MatchContext ctx) {
        Channel ch = channel(e);
        List<OutboundMessage> h = client.history(ch.chatId(), ch.threadId());
        int i = cursor(ch);
        if (i >= h.size()) {
            Assertions.fail("Expected a message in " + ch + " satisfying "
                    + describe(e.predicates()) + " but no further output was sent.");
        }
        OutboundMessage m = h.get(i);
        Integer statusId = client.statusMessageId(ch.chatId(), ch.threadId());
        for (Scenario.Step.Predicate p : e.predicates()) {
            if (!satisfies(p, m, ctx, statusId)) {
                Assertions.fail("Next message in " + ch + " did not satisfy " + describe(p)
                        + ".\nGot: " + render(m) + "\nFull predicate: " + describe(e.predicates()));
            }
        }
        cursors.put(ch, i + 1);
    }

    /** Assert the channel's single live status message currently matches; does not consume the stream cursor. */
    private void expectStatus(Scenario.Step.ExpectStatus e, MatchContext ctx) {
        Channel ch = channel(e);
        OutboundMessage status = client.statusMessage(ch.chatId(), ch.threadId());
        if (status == null) {
            Assertions.fail("Expected a live status message in " + ch + " matching \"" + e.pattern()
                    + "\" but none was posted.");
        }
        int creates = client.statusCreateCount(ch.chatId(), ch.threadId());
        Assertions.assertEquals(1, creates,
                () -> "Status message in " + ch + " was created " + creates
                        + " times — it must be edited in place, not re-posted.");
        if (!LineMatcher.matches(e.pattern(), status.text(), ctx)) {
            Assertions.fail("Live status in " + ch + " did not match \"" + e.pattern() + "\".\nGot: \""
                    + (status.text() == null ? "" : status.text()) + "\"");
        }
    }

    private boolean satisfies(Scenario.Step.Predicate p, OutboundMessage m, MatchContext ctx, Integer statusId) {
        return switch (p) {
            case Scenario.Step.Predicate.Text t -> !m.hasPhoto() && LineMatcher.matches(t.pattern(), m.text(), ctx);
            case Scenario.Step.Predicate.Image im -> m.hasPhoto()
                    && LineMatcher.matches(im.captionGlob(), m.text() == null ? "" : m.text(), ctx);
            case Scenario.Step.Predicate.Button b -> satisfiesButtons(b, m.buttons(), ctx);
            case Scenario.Step.Predicate.Backlink b -> statusId != null
                    && m.replyToMessageId() != null && m.replyToMessageId().equals(statusId);
            case Scenario.Step.Predicate.Status s ->
                    throw new IllegalStateException("<status> is asserted as its own line, not against a stream message");
        };
    }

    private boolean satisfiesButtons(Scenario.Step.Predicate.Button b, List<Button> buttons, MatchContext ctx) {
        if (buttons.isEmpty()) return false;
        if (b.count() != null && buttons.size() != b.count()) return false;
        if (!buttons.stream().allMatch(btn -> LineMatcher.matches(b.glob(), btn.label(), ctx))) return false;
        if (b.distinct() && buttons.stream().map(Button::label).distinct().count() != buttons.size()) return false;
        return true;
    }

    private void expectDrain(Scenario.Step.ExpectDrain e) {
        Channel ch = channel(e);
        Assertions.assertTrue(cursor(ch) >= client.history(ch.chatId(), ch.threadId()).size(),
                () -> "Expected no further output in " + ch + " but saw:" + renderFrom(ch));
    }

    private static String describe(Scenario.Step.Predicate p) {
        return switch (p) {
            case Scenario.Step.Predicate.Text t -> "text \"" + t.pattern() + "\"";
            case Scenario.Step.Predicate.Image im -> "image caption \"" + im.captionGlob() + "\"";
            case Scenario.Step.Predicate.Button b -> (b.count() != null ? "exactly " + b.count() + " " : "")
                    + (b.distinct() ? "distinct " : "") + "buttons all matching \"" + b.glob() + "\"";
            case Scenario.Step.Predicate.Status s -> "live status matching \"" + s.pattern() + "\"";
            case Scenario.Step.Predicate.Backlink b -> "a backlink (reply) to the status message";
        };
    }

    private static String describe(List<Scenario.Step.Predicate> ps) {
        return ps.stream().map(ScenarioRunnerTest::describe).collect(java.util.stream.Collectors.joining(" + "));
    }

    private void executeTap(Scenario.Step.Tap tap, MatchContext ctx) {
        // Scan the whole channel history backward (not the cursor) for the most recent message with a
        // button matching the pattern, then replay its callback data.
        List<OutboundMessage> h = client.history(tap.chatId(), tap.threadId());
        for (int i = h.size() - 1; i >= 0; i--) {
            for (Button b : h.get(i).buttons()) {
                if (LineMatcher.matches(tap.buttonGlob(), b.label(), ctx)) {
                    client.deliverTap(tap.from().name(), tap.from().userId(), tap.chatId(), tap.threadId(), b.callbackData());
                    return;
                }
            }
        }
        Assertions.fail("No button matching \"" + tap.buttonGlob() + "\" found in chat " + tap.chatId()
                + " to press");
    }

    // The {leader} placeholder must match both how leaders appear in message text (full name, e.g.
    // "America Abraham Lincoln") and on buttons (compact display name, e.g. "Lincoln",
    // "Kublai (China)"). Since matching is whole-string, the alternation needs both spellings.
    private List<String> leaderNames() {
        return leaderService.getLeaders().stream()
                .flatMap(l -> Stream.of(l.getFullName(), l.getDisplayName()))
                .distinct()
                .toList();
    }

    private static Channel channel(Scenario.Step step) {
        return new Channel(step.chatId(), step.threadId());
    }

    private int cursor(Channel ch) {
        return cursors.getOrDefault(ch, 0);
    }

    private void resetState() {
        new HashSet<>(lobbyService.getAllLobbies().keySet()).forEach(lobbyService::removeLobby);
        client.reset();
        cursors = new HashMap<>();
    }

    /** Render the still-unconsumed tail of a channel's history for failure diagnostics. */
    private String renderFrom(Channel ch) {
        List<OutboundMessage> h = client.history(ch.chatId(), ch.threadId());
        int from = cursor(ch);
        if (from >= h.size()) return "(nothing)";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < h.size(); i++) {
            sb.append("\n  - ").append(render(h.get(i)));
        }
        return sb.toString();
    }

    private static String render(OutboundMessage m) {
        String label = m.hasPhoto() ? "caption" : "text";
        StringBuilder sb = new StringBuilder();
        sb.append(m.kind()).append(' ').append(label).append('=')
                .append(m.text() == null ? "" : '"' + m.text() + '"');
        if (!m.buttons().isEmpty()) {
            sb.append(" buttons=").append(m.buttons().stream().map(Button::label).toList());
        }
        return sb.toString();
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
