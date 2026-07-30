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
 * <p>Discord allows <b>5 action rows</b> per message, each holding up to 5 buttons. So the 89-leader
 * Herson grid — one flat inline keyboard on Telegram — becomes a <b>paged grid</b>: 4 rows of 5
 * leaders, plus one row combining page navigation with whatever actions the view declared. Callers
 * never see that; they ask for a chooser and get whatever fits.
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
    /** Options per page once a chooser needs paging: 4 rows of 5, leaving the 5th row for controls. */
    static final int OPTIONS_PER_PAGE = (MAX_ACTION_ROWS - 1) * MAX_BUTTONS_PER_ROW;
    /** A chooser this size or smaller fits on one page and needs no navigation at all. */
    static final int BUTTON_GRID_LIMIT = MAX_ACTION_ROWS * MAX_BUTTONS_PER_ROW;

    /** Reserved payload prefix for adapter-internal navigation, never seen by the dispatcher. */
    static final String PAGE_VERB = "__page";
    private static final String NOOP_ID = "__noop";

    public List<ActionRow> render(List<Component> components) {
        return render(components, 0);
    }

    /** Render with a chooser scrolled to {@code page} — the page a message is currently showing. */
    public List<ActionRow> render(List<Component> components, int page) {
        if (components == null || components.isEmpty()) return List.of();

        Component.Chooser chooser = choosers(components).stream().findFirst().orElse(null);
        List<Component.ActionButton> actions = components.stream()
                .filter(Component.Actions.class::isInstance)
                .map(Component.Actions.class::cast)
                .flatMap(a -> a.buttons().stream())
                .toList();

        if (chooser == null) {
            return actions.isEmpty() ? List.of() : List.of(ActionRow.of(buttons(actions)));
        }

        if (!needsPaging(chooser)) {
            List<ActionRow> rows = new ArrayList<>(buttonGrid(chooser));
            if (!actions.isEmpty()) rows.add(ActionRow.of(buttons(actions)));
            return capRows(rows, actions);
        }

        int pages = pageCount(chooser);
        int current = Math.floorMod(page, pages);
        List<Component.Option> visible = chooser.options().subList(
                current * OPTIONS_PER_PAGE,
                Math.min((current + 1) * OPTIONS_PER_PAGE, chooser.options().size()));

        List<ActionRow> rows = new ArrayList<>(buttonGrid(
                new Component.Chooser(chooser.id(), chooser.placeholder(), visible,
                        chooser.preferredColumns(), chooser.selection())));
        rows.add(ActionRow.of(controlRow(current, pages, actions)));
        return rows;
    }

    /** True when the whole option list cannot be shown at once. */
    boolean needsPaging(Component.Chooser chooser) {
        return chooser.options().size() > BUTTON_GRID_LIMIT;
    }

    int pageCount(Component.Chooser chooser) {
        return (int) Math.ceil(chooser.options().size() / (double) OPTIONS_PER_PAGE);
    }

    /** Any chooser in the declaration — used to decide whether a message needs page tracking. */
    List<Component.Chooser> choosers(List<Component> components) {
        return components.stream()
                .filter(Component.Chooser.class::isInstance)
                .map(Component.Chooser.class::cast)
                .toList();
    }

    /**
     * Navigation and the view's own actions share the last row, which is the only way 20 leaders plus
     * Submit and Reset fit inside five rows: ◀ · page · ▶ · SUBMIT · RESET is exactly five buttons.
     */
    private List<ItemComponent> controlRow(int current, int pages, List<Component.ActionButton> actions) {
        List<ItemComponent> row = new ArrayList<>();
        row.add(Button.secondary(PAGE_VERB + " " + Math.floorMod(current - 1, pages), "◀"));
        row.add(Button.secondary(NOOP_ID, (current + 1) + "/" + pages).asDisabled());
        row.add(Button.secondary(PAGE_VERB + " " + Math.floorMod(current + 1, pages), "▶"));
        for (Component.ActionButton action : actions) {
            if (row.size() == MAX_BUTTONS_PER_ROW) break;
            row.add(Button.of(styleOf(action.style()), action.action().encode(),
                    trim(action.label(), MAX_LABEL_LENGTH)));
        }
        return row;
    }

    private List<ItemComponent> buttons(List<Component.ActionButton> actions) {
        return actions.stream()
                .limit(MAX_BUTTONS_PER_ROW)
                .map(b -> (ItemComponent) Button.of(styleOf(b.style()), b.action().encode(),
                        trim(b.label(), MAX_LABEL_LENGTH)))
                .toList();
    }

    /** Keep the action row even if a chooser would otherwise crowd it out. */
    private List<ActionRow> capRows(List<ActionRow> rows, List<Component.ActionButton> actions) {
        if (rows.size() <= MAX_ACTION_ROWS) return rows;
        List<ActionRow> capped = new ArrayList<>(rows.subList(0, MAX_ACTION_ROWS - (actions.isEmpty() ? 0 : 1)));
        if (!actions.isEmpty()) capped.add(ActionRow.of(buttons(actions)));
        return capped;
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
