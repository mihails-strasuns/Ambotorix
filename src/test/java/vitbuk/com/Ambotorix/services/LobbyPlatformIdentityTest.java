package vitbuk.com.Ambotorix.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vitbuk.com.Ambotorix.chat.ChatRef;
import vitbuk.com.Ambotorix.chat.Platform;
import vitbuk.com.Ambotorix.chat.UserRef;
import vitbuk.com.Ambotorix.entities.Player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Players must keep the platform they joined on.
 *
 * <p>They did not: registration used to build every {@code Player} through a constructor that stamped
 * {@code UserRef.telegram(...)}, so Discord players were recorded as Telegram users holding a Discord
 * snowflake. DMing one then routed to the Telegram gateway — a crash on a Discord-only deployment, and
 * on a dual deployment something worse: a Telegram API call with a snowflake id, failing silently into
 * "couldn't DM you".
 */
class LobbyPlatformIdentityTest {

    private LobbyService service;

    @BeforeEach
    void setUp() {
        service = new LobbyService();
    }

    @Test
    void discordLobbyKeepsPlayersOnDiscord() {
        ChatRef channel = ChatRef.discord("1532288335630962830");
        UserRef host = new UserRef(Platform.DISCORD, "246604180016234497", "alice", "Alice");
        UserRef joiner = new UserRef(Platform.DISCORD, "310923142981287936", "bob", "Bob");

        service.createLobby(channel, new Player(host));
        service.registerPlayer(channel, new Player(joiner).getUser());

        for (Player player : service.getLobby(channel).getPlayers()) {
            assertEquals(Platform.DISCORD, player.getUser().platform(),
                    () -> player.getUserName() + " must stay a Discord user, or their DM routes to Telegram");
        }
    }

    @Test
    void telegramLobbyKeepsPlayersOnTelegram() {
        ChatRef group = ChatRef.telegram(-1001234567890L, null);

        service.createLobby(group, new Player(UserRef.telegram(11L, "alice")));
        service.registerPlayer(group, UserRef.telegram(22L, "bob"));

        for (Player player : service.getLobby(group).getPlayers()) {
            assertEquals(Platform.TELEGRAM, player.getUser().platform());
        }
    }

    @Test
    void treatingADiscordUserAsATelegramOneFailsLoudly() {
        UserRef discordUser = new UserRef(Platform.DISCORD, "310923142981287936", "bob", "Bob");

        // A snowflake parses as a long perfectly well, which is exactly why this has to throw
        // instead of quietly handing back a plausible-looking Telegram id.
        assertThrows(IllegalStateException.class, discordUser::asTelegramUserId);
    }
}
