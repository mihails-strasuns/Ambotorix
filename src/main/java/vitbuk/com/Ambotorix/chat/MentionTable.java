package vitbuk.com.Ambotorix.chat;

import java.util.Map;

/**
 * Resolves the {@code @username} placeholders in message text to real users.
 *
 * <p>Message text carries mentions as plain {@code @name}, which is already correct on Telegram; the
 * Discord renderer rewrites a name to {@code <@id>} <em>only</em> when this table knows it. Anything
 * unresolved — {@code @VitBuk} in /credits, an {@code @} inside a leader description — stays literal,
 * which is what makes a bare placeholder safe without a sigil.
 */
public record MentionTable(Map<String, UserRef> byUserName) {

    public static final MentionTable EMPTY = new MentionTable(Map.of());

    public static MentionTable of(Map<String, UserRef> byUserName) {
        return byUserName == null || byUserName.isEmpty() ? EMPTY : new MentionTable(Map.copyOf(byUserName));
    }

    /** Case-insensitive lookup — platforms differ on handle casing. */
    public UserRef resolve(String userName) {
        if (userName == null) return null;
        UserRef exact = byUserName.get(userName);
        if (exact != null) return exact;
        return byUserName.entrySet().stream()
                .filter(e -> e.getKey().equalsIgnoreCase(userName))
                .map(Map.Entry::getValue)
                .findFirst().orElse(null);
    }
}
