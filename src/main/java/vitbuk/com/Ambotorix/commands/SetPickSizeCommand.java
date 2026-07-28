package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class SetPickSizeCommand implements HostCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/setPickSize", "/setPickSize [n]", "Set number of leaders per pick pool");

    @Override public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        try {
            service.sendSetPickSize(ctx, Integer.parseInt(ctx.args()));
        } catch (NumberFormatException e) {
            service.sendMessage(ctx, "Invalid number: " + ctx.args());
        }
    }
}
