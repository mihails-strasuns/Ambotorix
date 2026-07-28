package vitbuk.com.Ambotorix.commands.structure;

import vitbuk.com.Ambotorix.chat.ChatEvent;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.UserRef;

/**
 * Everything a command needs about its invocation: who ran it, where, and with what argument.
 *
 * <p>The argument is already separated from the command name by the adapter, so commands no longer
 * slice raw message text — which is what let them stop depending on a platform's message type.
 */
public record CommandContext(ChatEvent.SlashCommand event) {

    public ChatRef chat() { return event.chat(); }

    public UserRef user() { return event.from(); }

    /** Everything after the command name, trimmed; empty (never null) when none was given. */
    public String args() { return event.args() == null ? "" : event.args().trim(); }

    public boolean direct() { return event.direct(); }
}
