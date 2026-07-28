package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class UpdateCommand implements GeneralCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/update", "/update",
            "Check for BBG updates and refresh leader data if a new version is available");

    @Override public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendUpdate(ctx);
    }
}
