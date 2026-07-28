package vitbuk.com.Ambotorix.chat;

/**
 * A destination: a channel on a platform, optionally a thread/topic inside it.
 *
 * <p>Ids are strings on purpose — Telegram uses signed longs, Discord unsigned snowflakes, and a
 * uniform string keeps both printable in logs and admin output without sign/parse traps.
 */
public record ChatRef(Platform platform, String channelId, String threadId) {

    public static ChatRef telegram(Long chatId, Integer threadId) {
        return new ChatRef(Platform.TELEGRAM, String.valueOf(chatId), threadId == null ? null : String.valueOf(threadId));
    }

    public static ChatRef discord(String channelId) {
        return new ChatRef(Platform.DISCORD, channelId, null);
    }

    /**
     * The identity a lobby is keyed by: platform + channel, with any thread dropped.
     *
     * <p>Telegram lobbies live at channel level — one lobby per group, remembering which forum topic
     * it posts into — so the thread must not be part of the key. Discord threads are channels in
     * their own right, so they get per-thread lobbies for free without a different rule.
     */
    public ChatRef channelKey() {
        return threadId == null ? this : new ChatRef(platform, channelId, null);
    }

    /** The channel id as a Telegram chat id. Only valid on {@link Platform#TELEGRAM} refs. */
    public long asTelegramChatId() {
        return Long.parseLong(channelId);
    }

    /** The thread id as a Telegram {@code message_thread_id}, or null for the General topic. */
    public Integer asTelegramThreadId() {
        return threadId == null ? null : Integer.valueOf(threadId);
    }

    @Override
    public String toString() {
        return platform + ":" + channelId + (threadId == null ? "" : "/" + threadId);
    }
}
