package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class SetBanSizeCommand implements HostCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/setBanSize", "/setBanSize [n]", "Set number of bans per player");

    @Override public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        try {
            service.sendSetBanSize(ctx, Integer.parseInt(ctx.args()));
        } catch (NumberFormatException e) {
            service.sendMessage(ctx, "Invalid number: " + ctx.args());
        }
    }
}
