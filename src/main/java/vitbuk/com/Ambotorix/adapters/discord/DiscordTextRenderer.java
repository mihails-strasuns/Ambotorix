package vitbuk.com.Ambotorix.adapters.discord;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.MentionTable;
import vitbuk.com.Ambotorix.chat.UserRef;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the bot's shared message dialect into Discord markdown.
 *
 * <p>Message text is authored once, in the HTML subset Telegram understands, with {@code @username}
 * as a mention placeholder (MULTIPLATFORM_PLAN.md D6). Telegram sends that as-is; Discord needs two
 * translations, both of which live here:
 *
 * <ul>
 *   <li><b>Markup</b> — {@code <b>} → {@code **}, {@code <i>} → {@code *}, {@code <code>} → backticks,
 *       {@code <pre>} → a fenced block, {@code <a href>} → {@code [label](url)}.</li>
 *   <li><b>Mentions</b> — {@code @alice} → {@code <@123>}, but <em>only</em> for names the
 *       {@link MentionTable} knows. An unknown {@code @handle} is left alone, which is what keeps a
 *       bare placeholder safe.</li>
 * </ul>
 */
@Service
public class DiscordTextRenderer {

    /** Discord's message content limit; embeds allow more but plain messages do not. */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    private static final Pattern TAG = Pattern.compile("<(/?)(b|strong|i|em|code|pre|u|s)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK = Pattern.compile("<a\\s+href=\"([^\"]*)\"\\s*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MENTION = Pattern.compile("@([A-Za-z0-9_.]{2,32})");

    public String render(String html, MentionTable mentions) {
        if (html == null || html.isEmpty()) return "";
        String out = LINK.matcher(html).replaceAll(m -> Matcher.quoteReplacement(
                "[" + m.group(2) + "](" + m.group(1) + ")"));
        out = TAG.matcher(out).replaceAll(m -> Matcher.quoteReplacement(markdownFor(m.group(2).toLowerCase())));
        out = unescapeEntities(out);
        return applyMentions(out, mentions);
    }

    private String markdownFor(String tag) {
        return switch (tag) {
            case "b", "strong" -> "**";
            case "i", "em" -> "*";
            case "code" -> "`";
            case "pre" -> "```";
            case "u" -> "__";
            case "s" -> "~~";
            default -> "";
        };
    }

    /**
     * HTML entities exist because Telegram's parser needs them; Discord shows them literally, so they
     * have to come back out.
     */
    private String unescapeEntities(String text) {
        return text.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    private String applyMentions(String text, MentionTable mentions) {
        if (mentions == null || mentions.byUserName().isEmpty()) return text;
        return MENTION.matcher(text).replaceAll(m -> {
            UserRef user = mentions.resolve(m.group(1));
            // Unknown handle, or a user we cannot address — leave the text exactly as written.
            return Matcher.quoteReplacement(user == null || !user.isAddressable()
                    ? m.group(0)
                    : "<@" + user.id() + ">");
        });
    }

    /**
     * Split rendered text into chunks Discord will accept, preferring line boundaries so a long
     * {@code /help} or roster listing breaks between entries rather than mid-word.
     */
    public List<String> chunk(String text, int limit) {
        if (text == null || text.isEmpty()) return List.of("");
        if (text.length() <= limit) return List.of(text);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            // A single line longer than the limit has no boundary to break on — hard-split it.
            while (line.length() > limit) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.add(line.substring(0, limit));
                line = line.substring(limit);
            }
            if (current.length() + line.length() + 1 > limit) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        return chunks;
    }
}
