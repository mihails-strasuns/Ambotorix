package vitbuk.com.Ambotorix.view;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.commands.MapAddCommand;
import vitbuk.com.Ambotorix.commands.MapRemoveCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.entities.CivMap;

import java.util.List;

/** The two map choosers: every map (to add) and the current pool (to remove). */
@Service
public class MapPoolView {

    private final CommandFactory commandFactory;

    public MapPoolView(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    public Component.Chooser addable(List<CivMap> maps, String lobbyToken) {
        String prefix = commandFactory.infoOf(MapAddCommand.class).prefix();
        List<Component.Option> options = maps.stream()
                .map(m -> Component.Option.of(m.name(), new ActionRef(prefix, lobbyToken, m.toString())))
                .toList();
        return Component.Chooser.of("map-add", "Add a map to the pool", options, 1);
    }

    public Component.Chooser removable(List<CivMap> maps, String lobbyToken) {
        String prefix = commandFactory.infoOf(MapRemoveCommand.class).prefix();
        List<Component.Option> options = maps.stream()
                .map(m -> Component.Option.of("❌ " + m, new ActionRef(prefix, lobbyToken, m.toString())))
                .toList();
        return Component.Chooser.of("map-remove", "Remove a map from the pool", options, 1);
    }
}
