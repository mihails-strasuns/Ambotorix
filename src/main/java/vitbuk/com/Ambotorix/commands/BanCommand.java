package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.PlayerCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class BanCommand implements PlayerCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/ban",
            "/ban [shortName]",
            "Ban leader for current lobby");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        // The argument is a free-form query; the matcher handles formatting, typos and partial names.
        service.sendSmartBan(ctx, ctx.args());
    }
}
