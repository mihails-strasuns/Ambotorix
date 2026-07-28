package vitbuk.com.Ambotorix.view;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.commands.DescriptionCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.entities.Leader;

import java.util.List;

/** Leaders offered as a chooser whose options open a leader's description. */
@Service
public class LeaderGridView {

    /**
     * Leaders per row in the compact grid. Options carry {@link Leader#getDisplayName()}, so three
     * fit a Telegram row without wrapping; on Discord the number is advisory (see the chooser policy).
     */
    private static final int GRID_COLUMNS = 3;

    private final CommandFactory commandFactory;

    public LeaderGridView(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    /** Compact grid of short display names — the roster browser behind /leaders. */
    public Component.Chooser grid(List<Leader> leaders) {
        return chooser("leaders", "Pick a leader to read about", leaders, GRID_COLUMNS, false);
    }

    /** One leader per row, labelled with the full name — used where vertical space is cheap (DMs). */
    public Component.Chooser list(List<Leader> leaders) {
        return chooser("leaders", "Pick a leader to read about", leaders, 1, true);
    }

    private Component.Chooser chooser(String id, String placeholder, List<Leader> leaders, int columns, boolean fullNames) {
        String prefix = commandFactory.infoOf(DescriptionCommand.class).prefix();
        List<Component.Option> options = leaders.stream()
                .map(l -> Component.Option.of(fullNames ? l.getFullName() : l.getDisplayName(),
                        new ActionRef(prefix, l.getShortName())))
                .toList();
        return Component.Chooser.of(id, placeholder, options, columns);
    }
}
