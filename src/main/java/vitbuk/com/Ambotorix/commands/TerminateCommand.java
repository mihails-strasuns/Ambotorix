package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class TerminateCommand implements HostCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/terminate",
            "/terminate",
            "Terminate current lobby");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendTerminate(ctx);
    }
}
