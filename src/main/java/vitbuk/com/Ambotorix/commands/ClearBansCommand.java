package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class ClearBansCommand implements HostCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/clearBans",
            "/clearBans",
            "Clear all bans so players can redo their bans (Host command)");

    @Override
    public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendClearBans(ctx);
    }
}
