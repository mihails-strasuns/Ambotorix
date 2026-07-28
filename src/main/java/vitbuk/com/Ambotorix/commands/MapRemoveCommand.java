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
public class MapRemoveCommand implements HostCommand, DynamicCommand {
    private static final CommandInfo INFO = new CommandInfo(
            "/mapRemove",
            "/mapRemove [mapName]",
            "Removes map from mappool of the current lobby");
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
        service.sendMapRemove(ctx.event(), maybeMap.get());
    }
}
