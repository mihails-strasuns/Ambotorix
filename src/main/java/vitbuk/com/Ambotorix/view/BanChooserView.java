package vitbuk.com.Ambotorix.view;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.commands.BanCommand;
import vitbuk.com.Ambotorix.commands.structure.CommandFactory;
import vitbuk.com.Ambotorix.entities.Leader;

import java.util.List;

/**
 * Leaders offered for banning. The lobby's channel travels in the action payload because the buttons
 * are tapped in a DM, where nothing else identifies which lobby the ban belongs to.
 */
@Service
public class BanChooserView {

    private final CommandFactory commandFactory;

    public BanChooserView(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    public Component.Chooser chooser(List<Leader> leaders, String lobbyToken) {
        String prefix = commandFactory.infoOf(BanCommand.class).prefix();
        List<Component.Option> options = leaders.stream()
                .map(l -> Component.Option.of(l.getFullName(),
                        new ActionRef(prefix, lobbyToken, l.getShortName())))
                .toList();
        return Component.Chooser.of("ban", "Choose a leader to ban", options, 1);
    }
}
