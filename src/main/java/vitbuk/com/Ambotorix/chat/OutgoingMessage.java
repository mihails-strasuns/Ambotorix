package vitbuk.com.Ambotorix.chat;

import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One message the bot wants delivered, described platform-neutrally.
 *
 * <p>{@code text} is the shared HTML subset ({@code <b> <i> <code> <pre> <a>}) with {@code @username}
 * mention placeholders; each adapter renders it for its own platform (MULTIPLATFORM_PLAN.md D6).
 * {@code silent} suppresses the notification — it is what marks the lobby's ambient status message,
 * as opposed to the milestone replies that are meant to ping.
 */
public record OutgoingMessage(Audience to, String text, MentionTable mentions,
                              List<Component> components, Attachment attachment,
                              MessageRef replyTo, boolean silent) {

    public static Builder to(Audience to) {
        return new Builder(to);
    }

    public static Builder to(ChatRef chat) {
        return new Builder(Audience.of(chat));
    }

    public static Builder to(UserRef user) {
        return new Builder(Audience.of(user));
    }

    public boolean hasAttachment() {
        return attachment != null;
    }

    public static final class Builder {
        private final Audience to;
        private String text;
        private MentionTable mentions = MentionTable.EMPTY;
        private final List<Component> components = new ArrayList<>();
        private Attachment attachment;
        private MessageRef replyTo;
        private boolean silent;

        private Builder(Audience to) {
            this.to = to;
        }

        public Builder text(String text) { this.text = text; return this; }
        public Builder mentions(MentionTable mentions) { this.mentions = mentions == null ? MentionTable.EMPTY : mentions; return this; }
        public Builder component(Component component) { if (component != null) this.components.add(component); return this; }
        public Builder components(List<Component> components) { if (components != null) this.components.addAll(components); return this; }
        public Builder attachment(Attachment attachment) { this.attachment = attachment; return this; }
        public Builder replyTo(MessageRef replyTo) { this.replyTo = replyTo; return this; }
        public Builder silent(boolean silent) { this.silent = silent; return this; }

        public OutgoingMessage build() {
            return new OutgoingMessage(to, text, mentions, List.copyOf(components), attachment, replyTo, silent);
        }
    }
}
