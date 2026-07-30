package vitbuk.com.Ambotorix.view;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.entities.Leader;

import java.util.List;

/** A player's secret-draft pool, one option per leader they may claim. */
@Service
public class PickPoolView {

    public static final String PICK_VERB = "/pick";

    public Component.Chooser chooser(List<Leader> pool, String lobbyToken) {
        List<Component.Option> options = pool.stream()
                .map(l -> Component.Option.of("Pick " + l.getFullName(),
                        new ActionRef(PICK_VERB, lobbyToken, l.getShortName())))
                .toList();
        return Component.Chooser.of("pick", "Pick your leader", options, 1);
    }
}
