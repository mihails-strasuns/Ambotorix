package vitbuk.com.Ambotorix.view;

import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ActionRef;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.chat.ui.Selection;
import vitbuk.com.Ambotorix.chat.ui.Style;
import vitbuk.com.Ambotorix.entities.Leader;

import java.util.List;

/**
 * The Herson ranked-pick UI: the whole unbanned roster as a ranked chooser, plus Submit and Reset.
 *
 * <p>This is the component the platforms disagree most about — ~89 options is one inline keyboard on
 * Telegram and four grouped select menus on Discord — and the reason {@code Component} describes
 * intent rather than widgets. Nothing here, and nothing in the Herson flow, knows which it becomes.
 */
@Service
public class HersonPickView {

    public static final String PICK_VERB = "/hpick";
    public static final String SUBMIT_VERB = "/hsubmit";
    public static final String RESET_VERB = "/hreset";
    public static final String CONFIRM_VERB = "/hconfirm";
    public static final String REDO_VERB = "/hredo";

    private static final int GRID_COLUMNS = 3;

    /**
     * The ranking grid. {@code ranked} is the player's current priority order (by shortname); an
     * option already in it carries its 1-based rank as a badge.
     */
    public List<Component> grid(List<Leader> available, List<String> ranked, String lobbyToken) {
        List<Component.Option> options = available.stream()
                .map(l -> {
                    int rank = ranked.indexOf(l.getShortName());
                    return Component.Option.of(l.getDisplayName(),
                            new ActionRef(PICK_VERB, lobbyToken, l.getShortName()),
                            rank >= 0 ? String.valueOf(rank + 1) : null);
                })
                .toList();

        return List.of(
                new Component.Chooser("herson-pick", "Rank your top 4", options, GRID_COLUMNS, Selection.RANKED),
                Component.Actions.of(
                        new Component.ActionButton("✅ SUBMIT", Style.PRIMARY, new ActionRef(SUBMIT_VERB, lobbyToken)),
                        new Component.ActionButton("🔄 RESET", Style.SECONDARY, new ActionRef(RESET_VERB, lobbyToken))));
    }

    /** Confirm / re-enter, offered when fuzzy matching had to correct a typed submission. */
    public Component.Actions confirm(String lobbyToken) {
        return Component.Actions.of(
                new Component.ActionButton("✅ Confirm", Style.PRIMARY, new ActionRef(CONFIRM_VERB, lobbyToken)),
                new Component.ActionButton("✏️ Re-enter", Style.SECONDARY, new ActionRef(REDO_VERB, lobbyToken)));
    }
}
