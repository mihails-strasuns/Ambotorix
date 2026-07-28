package vitbuk.com.Ambotorix.commands;

import org.springframework.stereotype.Component;
import vitbuk.com.Ambotorix.commands.structure.CommandContext;
import vitbuk.com.Ambotorix.commands.structure.CommandInfo;
import vitbuk.com.Ambotorix.commands.structure.DynamicCommand;
import vitbuk.com.Ambotorix.commands.structure.HostCommand;
import vitbuk.com.Ambotorix.entities.CivMap;
import vitbuk.com.Ambotorix.services.AmbotorixService;

import java.util.Optional;

@Component
public class MapAddCommand implements HostCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/mapAdd",
            "/mapAdd [map Name]",
            "Add map to a mappool of the lobby (Host command)");
    @Override
    public CommandInfo getInfo() {
        return INFO;
    }

    @Override
    public void execute(CommandContext ctx, AmbotorixService service) {
        Optional<CivMap> maybeMap = CivMap.fromDisplayNameIgnoreCase(ctx.args());
        if (maybeMap.isEmpty()) {
            service.sendNoSuchMap(ctx);
            return;
        }
        service.sendMapAdd(ctx.event(), maybeMap.get());
    }
}
