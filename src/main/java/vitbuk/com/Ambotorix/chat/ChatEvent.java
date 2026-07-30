package vitbuk.com.Ambotorix.chat;

import java.util.List;

/**
 * Something a user did, normalized across platforms.
 *
 * <p>All platform parsing — stripping {@code @botname}, splitting a command from its argument,
 * reassembling Discord slash-command options, decoding a {@code custom_id} — happens in the adapter,
 * so the dispatcher below this type never sees a Telegram {@code Update} or a JDA event.
 */
public sealed interface ChatEvent {

    UserRef from();

    /** Where it happened: the group channel, or the user's private chat with the bot. */
    ChatRef chat();

    /** True when this arrived in a DM rather than a group channel. */
    boolean direct();

    /** A slash command with everything after the command name as one argument string. */
    record SlashCommand(UserRef from, ChatRef chat, boolean direct, String prefix, String args)
            implements ChatEvent {

        public boolean hasArgs() {
            return args != null && !args.isBlank();
        }
    }

    /** Free text with no command — how Herson ranked picks arrive. */
    record FreeText(UserRef from, ChatRef chat, boolean direct, String text) implements ChatEvent {}

    /**
     * A button tap or menu selection. {@code chat} is where the component was displayed, which is
     * usually a DM — the lobby it acts on travels in the action payload instead.
     */
    record Interaction(UserRef from, ChatRef chat, boolean direct, ActionRef action, List<String> values)
            implements ChatEvent {}
}
