package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.AdminCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.services.AmbotorixService;

@Component
public class AdminTerminateCommand implements AdminCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/adminTerminate", "/adminTerminate [chatId]", "Force-terminate a lobby by chatId (Admin only)");

    @Override public CommandInfo getInfo() { return INFO; }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        service.sendAdminTerminate(ctx, ctx.args());
    }
}
