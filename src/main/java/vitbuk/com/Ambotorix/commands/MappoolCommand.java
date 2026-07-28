package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class MappoolCommand implements HostCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/mappool",
            "/mappool",
            "Shows mappool of the lobby");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendMappool(ctx);
    }
}
