package vitbuk.com.Ambotorix.chat;

/**
 * An actor on a platform.
 *
 * <p>{@code userName} is the handle the lobby keys players by and the one {@code @mentions} in
 * message text resolve against; {@code displayName} is cosmetic. {@code id} is what a DM is
 * addressed to and what Discord mentions render from.
 */
public record UserRef(Platform platform, String id, String userName, String displayName) {

    public static UserRef telegram(Long userId, String userName) {
        return new UserRef(Platform.TELEGRAM, userId == null ? null : String.valueOf(userId), userName, userName);
    }

    /** True when the bot has no way to address this user directly (no id known yet). */
    public boolean isAddressable() {
        return id != null;
    }

    public Long asTelegramUserId() {
        return id == null ? null : Long.valueOf(id);
    }
}
