package vitbuk.com.Ambotorix.adapters.discord;

import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.MentionTable;
import vitbuk.com.Ambotorix.chat.Platform;
import vitbuk.com.Ambotorix.chat.UserRef;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These carry the weight a structured text AST would otherwise carry at compile time (D6), so they
 * are not optional: if this class is wrong, every Discord message is wrong.
 */
class DiscordTextRendererTest {

    private final DiscordTextRenderer renderer = new DiscordTextRenderer();

    private static MentionTable table(String userName, String id) {
        return MentionTable.of(Map.of(userName, new UserRef(Platform.DISCORD, id, userName, userName)));
    }

    @Test
    void convertsInlineMarkup() {
        assertEquals("**Lobby by alice**", renderer.render("<b>Lobby by alice</b>", MentionTable.EMPTY));
        assertEquals("*waiting*", renderer.render("<i>waiting</i>", MentionTable.EMPTY));
        assertEquals("`/pick gandhi`", renderer.render("<code>/pick gandhi</code>", MentionTable.EMPTY));
    }

    @Test
    void convertsPreBlocksSoTheLeaderboardTableSurvives() {
        String rendered = renderer.render("<pre>Player  Total\nShu B   4</pre>", MentionTable.EMPTY);
        assertEquals("```Player  Total\nShu B   4```", rendered);
    }

    @Test
    void convertsLinks() {
        assertEquals("[Join our Discord](https://discord.gg/x)",
                renderer.render("<a href=\"https://discord.gg/x\">Join our Discord</a>", MentionTable.EMPTY));
    }

    @Test
    void unescapesEntitiesTelegramNeededButDiscordShowsLiterally() {
        assertEquals("a < b & c > d", renderer.render("a &lt; b &amp; c &gt; d", MentionTable.EMPTY));
    }

    @Test
    void rewritesKnownMentionsOnly() {
        String rendered = renderer.render("@alice and @bob and @VitBuk", table("alice", "42"));

        assertEquals("<@42> and @bob and @VitBuk", rendered);
    }

    @Test
    void mentionLookupIgnoresHandleCasing() {
        assertEquals("<@42>", renderer.render("@Alice", table("alice", "42")));
    }

    @Test
    void leavesMentionsAloneWhenTheUserHasNoAddressableId() {
        MentionTable unaddressable = MentionTable.of(
                Map.of("alice", new UserRef(Platform.DISCORD, null, "alice", "alice")));

        assertEquals("@alice", renderer.render("@alice", unaddressable));
    }

    @Test
    void chunksLongTextOnLineBoundaries() {
        String text = ("line of text\n").repeat(400).trim();

        List<String> chunks = renderer.chunk(text, DiscordTextRenderer.MAX_MESSAGE_LENGTH);

        assertTrue(chunks.size() > 1, "expected the text to be split");
        chunks.forEach(c -> assertTrue(c.length() <= DiscordTextRenderer.MAX_MESSAGE_LENGTH,
                () -> "chunk too long: " + c.length()));
        assertEquals(text, String.join("\n", chunks), "chunking must not lose or reorder content");
    }

    @Test
    void hardSplitsASingleOverlongLine() {
        String text = "x".repeat(4500);

        List<String> chunks = renderer.chunk(text, DiscordTextRenderer.MAX_MESSAGE_LENGTH);

        assertEquals(3, chunks.size());
        assertEquals(text, String.join("", chunks));
    }
}
