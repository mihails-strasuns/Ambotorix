package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class DescriptionCommand implements GeneralCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/d",
            "/d [shortName]",
            "Show description of a leader");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendDescription(ctx.event(), ctx.args());
    }
}
