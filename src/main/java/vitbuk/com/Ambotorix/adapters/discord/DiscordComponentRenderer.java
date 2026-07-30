package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import org.springframework.stereotype.Service;
import vitbuk.com.Ambotorix.chat.ui.Component;
import vitbuk.com.Ambotorix.chat.ui.Selection;
import vitbuk.com.Ambotorix.chat.ui.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Discord's answer to "render these components", and the reason {@link Component} describes intent
 * rather than widgets.
 *
 * <p>Discord allows <b>5 action rows</b> per message, each holding up to 5 buttons — 25 buttons in
 * all. So the 89-leader Herson grid, which is one flat inline keyboard on Telegram, is split across
 * <b>several messages</b> of 25 buttons, with the view's own actions (Submit/Reset) on the last one.
 * Every leader is on screen and reachable by scrolling; nothing hides behind a page control. Callers
 * never see any of this — they ask for a chooser and get whatever fits.
 *
 * <p>Buttons rather than select menus, deliberately. A dropdown hides the options behind a click, and
 * — decisively — cannot express a <em>ranking</em>: Discord returns a multi-select's values in the
 * component's own order, not the order the user clicked, so "rank your top 4" is unrepresentable.
 * Buttons show every option and let each tap carry its own meaning.
 */
@Service
public class DiscordComponentRenderer {

    static final int MAX_ACTION_ROWS = 5;
    static final int MAX_BUTTONS_PER_ROW = 5;
    static final int MAX_OPTIONS_PER_MENU = 25;
    static final int MAX_LABEL_LENGTH = 80;
    /** Buttons one message can carry: 5 rows of 5. */
    static final int BUTTONS_PER_MESSAGE = MAX_ACTION_ROWS * MAX_BUTTONS_PER_ROW;
    /** Options on the final message, which also carries the Submit/Reset row. */
    static final int OPTIONS_WITH_ACTIONS = (MAX_ACTION_ROWS - 1) * MAX_BUTTONS_PER_ROW;

    /** All the rows of a declaration, for the common case that fits in one message. */
    public List<ActionRow> render(List<Component> components) {
        List<List<ActionRow>> chunks = renderChunks(components);
        return chunks.isEmpty() ? List.of() : chunks.get(0);
    }

    /**
     * Split a declaration into as many messages as Discord's 25-button ceiling requires.
     *
     * <p>One chunk per message, in order; the view's actions land on the final chunk so Submit sits
     * below the whole roster. A declaration that fits in 25 buttons yields exactly one chunk, which is
     * every case except the leader roster.
     */
    public List<List<ActionRow>> renderChunks(List<Component> components) {
        if (components == null || components.isEmpty()) return List.of();

        Component.Chooser chooser = components.stream()
                .filter(Component.Chooser.class::isInstance)
                .map(Component.Chooser.class::cast)
                .findFirst().orElse(null);
        List<Component.ActionButton> actions = components.stream()
                .filter(Component.Actions.class::isInstance)
                .map(Component.Actions.class::cast)
                .flatMap(a -> a.buttons().stream())
                .toList();

        if (chooser == null) {
            return actions.isEmpty() ? List.of() : List.of(List.of(ActionRow.of(buttons(actions))));
        }

        List<List<ActionRow>> chunks = new ArrayList<>();
        List<Component.Option> options = chooser.options();
        // A chunk carrying the actions can only hold four rows of options; the rest hold five.
        for (int from = 0; from < options.size(); ) {
            boolean lastChunk = from + BUTTONS_PER_MESSAGE >= options.size();
            int capacity = lastChunk && !actions.isEmpty() ? OPTIONS_WITH_ACTIONS : BUTTONS_PER_MESSAGE;
            int to = Math.min(from + capacity, options.size());
            List<ActionRow> rows = new ArrayList<>(buttonGrid(new Component.Chooser(
                    chooser.id(), chooser.placeholder(), options.subList(from, to),
                    chooser.preferredColumns(), chooser.selection())));
            from = to;
            if (from >= options.size() && !actions.isEmpty()) rows.add(ActionRow.of(buttons(actions)));
            chunks.add(List.copyOf(rows));
        }
        if (chunks.isEmpty() && !actions.isEmpty()) chunks.add(List.of(ActionRow.of(buttons(actions))));
        return List.copyOf(chunks);
    }

    /**
     * A cheap fingerprint of rendered rows — label plus payload per button. Comparing these is how the
     * gateway avoids re-editing messages whose buttons did not change.
     */
    List<String> signatureOf(List<ActionRow> rows) {
        return rows.stream()
                .flatMap(row -> row.getComponents().stream())
                .map(component -> component instanceof Button button
                        ? button.getLabel() + "\u0000" + button.getId()
                        : String.valueOf(component))
                .toList();
    }

    private List<ItemComponent> buttons(List<Component.ActionButton> actions) {
        return actions.stream()
                .limit(MAX_BUTTONS_PER_ROW)
                .map(b -> (ItemComponent) Button.of(styleOf(b.style()), b.action().encode(),
                        trim(b.label(), MAX_LABEL_LENGTH)))
                .toList();
    }

    private List<ActionRow> buttonGrid(Component.Chooser chooser) {
        List<ActionRow> rows = new ArrayList<>();
        List<ItemComponent> row = new ArrayList<>();
        for (Component.Option option : chooser.options()) {
            row.add(Button.secondary(option.action().encode(), label(option)));
            if (row.size() == MAX_BUTTONS_PER_ROW) {
                rows.add(ActionRow.of(row));
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) rows.add(ActionRow.of(row));
        return rows;
    }

    private ButtonStyle styleOf(Style style) {
        return switch (style) {
            case PRIMARY -> ButtonStyle.PRIMARY;
            case DANGER -> ButtonStyle.DANGER;
            case SECONDARY -> ButtonStyle.SECONDARY;
        };
    }

    private String label(Component.Option option) {
        String badge = option.badge();
        String label = badge == null || badge.isBlank() ? option.label() : option.label() + " (" + badge + ")";
        return trim(label, MAX_LABEL_LENGTH);
    }

    private String trim(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit - 1) + "…";
    }
}
