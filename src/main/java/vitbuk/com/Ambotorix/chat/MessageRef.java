package vitbuk.com.Ambotorix.chat;

/** A handle on a message the bot sent, so it can be edited or replied to later. */
public record MessageRef(ChatRef chat, String messageId) {

    public Integer asTelegramMessageId() {
        return messageId == null ? null : Integer.valueOf(messageId);
    }
}
