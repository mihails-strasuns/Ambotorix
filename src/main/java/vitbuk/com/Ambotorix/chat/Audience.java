package vitbuk.com.Ambotorix.chat;

/**
 * Who a message is for. The gateway resolves this to a concrete destination.
 *
 * <p>{@link Direct} means a DM on every platform — see MULTIPLATFORM_PLAN.md D6a for why Discord
 * ephemeral replies are deliberately not used.
 */
public sealed interface Audience {

    Platform platform();

    /** The lobby's group channel (or any channel the bot posts to). */
    record Channel(ChatRef chat) implements Audience {
        @Override public Platform platform() { return chat.platform(); }
    }

    /** A private message to one user. */
    record Direct(UserRef user) implements Audience {
        @Override public Platform platform() { return user.platform(); }
    }

    static Audience of(ChatRef chat) { return new Channel(chat); }
    static Audience of(UserRef user) { return new Direct(user); }
}
