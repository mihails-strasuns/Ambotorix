package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import vitbuk.com.Ambotorix.commands.structure.Command;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the bot's commands look like once published to Discord.
 *
 * <p>Written after {@code /lobby herson} silently created an <em>open</em> draft: the registrar only
 * gave an argument slot to {@link DynamicCommand}s, and on Discord an option that was never registered
 * cannot be typed — the text is simply discarded. Whether a command takes an argument has to come from
 * its usage string; the marker interface only says whether that argument is mandatory.
 */
@SpringBootTest
class DiscordSlashCommandRegistrarTest {

    @Autowired CommandFactory commandFactory;

    /** Build the registration data the way the registrar does, without needing a live JDA. */
    private SlashCommandData dataFor(String prefix) throws Exception {
        DiscordSlashCommandRegistrar registrar = new DiscordSlashCommandRegistrar(commandFactory);
        Method toSlashCommand = DiscordSlashCommandRegistrar.class
                .getDeclaredMethod("toSlashCommand", Command.class);
        toSlashCommand.setAccessible(true);
        return (SlashCommandData) toSlashCommand.invoke(registrar, commandFactory.getCommand(prefix));
    }

    @Test
    void optionalArgumentIsRegisteredButNotRequired() throws Exception {
        List<OptionData> options = dataFor("/lobby").getOptions();

        assertEquals(1, options.size(), "/lobby [draft] must offer an argument, or it cannot be passed");
        assertEquals(DiscordSlashCommandRegistrar.ARGS_OPTION, options.get(0).getName());
        assertFalse(options.get(0).isRequired(), "/lobby with no draft name is valid");
    }

    @Test
    void mandatoryArgumentIsRegisteredAsRequired() throws Exception {
        List<OptionData> options = dataFor("/ban").getOptions();

        assertEquals(1, options.size());
        assertTrue(options.get(0).isRequired(), "Discord should reject /ban with no leader itself");
    }

    @Test
    void commandsWithoutArgumentsGetNoOption() throws Exception {
        assertTrue(dataFor("/help").getOptions().isEmpty());
        assertTrue(dataFor("/register").getOptions().isEmpty());
    }

    @Test
    void everyCommandWhoseUsageShowsAnArgumentCanActuallyReceiveOne() throws Exception {
        for (Command command : commandFactory.getAll()) {
            boolean usageShowsArgument = command.getInfo().name().contains("[");
            boolean optionRegistered = !dataFor(command.getInfo().prefix()).getOptions().isEmpty();

            assertEquals(usageShowsArgument, optionRegistered,
                    () -> command.getInfo().name() + " advertises "
                            + (usageShowsArgument ? "an argument that Discord users cannot pass"
                                                  : "no argument but registers one"));
        }
    }
}
