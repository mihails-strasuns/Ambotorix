package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.GeneralCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class TimeCommand implements GeneralCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/time",
            "/time",
            "Shows current time for Riga and Munich");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendTime(ctx);
    }
}
