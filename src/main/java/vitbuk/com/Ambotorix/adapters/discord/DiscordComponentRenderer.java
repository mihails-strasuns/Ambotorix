package vitbuk.com.Ambotorix.adapters.discord;

import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
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
 * <p>Discord allows <b>5 action rows</b> per message, each holding either up to 5 buttons or exactly
 * one select menu of up to 25 options. So the 89-leader Herson grid — one flat inline keyboard on
 * Telegram — has to become four alphabetically bucketed select menus plus a row of actions. Callers
 * never see that: they ask for a chooser and get whatever fits.
 */
@Service
public class DiscordComponentRenderer {

    static final int MAX_ACTION_ROWS = 5;
    static final int MAX_BUTTONS_PER_ROW = 5;
    static final int MAX_OPTIONS_PER_MENU = 25;
    static final int MAX_LABEL_LENGTH = 80;
    /** Above this many options a chooser can no longer be buttons and becomes select menus. */
    static final int BUTTON_GRID_LIMIT = MAX_ACTION_ROWS * MAX_BUTTONS_PER_ROW;

    public List<ActionRow> render(List<Component> components) {
        if (components == null || components.isEmpty()) return List.of();

        // Actions are rendered last so they land on the final row, below any chooser.
        List<ActionRow> chooserRows = new ArrayList<>();
        List<ActionRow> actionRows = new ArrayList<>();
        for (Component component : components) {
            switch (component) {
                case Component.Chooser chooser -> chooserRows.addAll(renderChooser(chooser));
                case Component.Actions actions -> actionRows.addAll(renderActions(actions));
            }
        }

        List<ActionRow> rows = new ArrayList<>(chooserRows);
        rows.addAll(actionRows);
        if (rows.size() > MAX_ACTION_ROWS) {
            // Keep the actions: a grid the player cannot submit is worse than a truncated grid.
            int keepChoosers = Math.max(0, MAX_ACTION_ROWS - actionRows.size());
            rows = new ArrayList<>(chooserRows.subList(0, Math.min(keepChoosers, chooserRows.size())));
            rows.addAll(actionRows);
        }
        return rows;
    }

    private List<ActionRow> renderChooser(Component.Chooser chooser) {
        return chooser.options().size() <= BUTTON_GRID_LIMIT && chooser.selection() != Selection.RANKED
                ? buttonGrid(chooser)
                : selectMenus(chooser);
    }

    /** Small chooser (maps, ban disambiguation): plain buttons, closest to the Telegram look. */
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

    /**
     * Large chooser (the leader roster): bucket the options into select menus of 25, labelled with
     * the range they cover so a player can find a civ without scrolling every menu.
     */
    private List<ActionRow> selectMenus(Component.Chooser chooser) {
        List<Component.Option> options = chooser.options();
        List<ActionRow> rows = new ArrayList<>();
        for (int start = 0; start < options.size(); start += MAX_OPTIONS_PER_MENU) {
            List<Component.Option> bucket = options.subList(start, Math.min(start + MAX_OPTIONS_PER_MENU, options.size()));
            StringSelectMenu.Builder menu = StringSelectMenu.create(chooser.id() + ":" + (start / MAX_OPTIONS_PER_MENU))
                    .setPlaceholder(placeholderFor(chooser, bucket))
                    .setRequiredRange(1, 1);
            for (Component.Option option : bucket) {
                SelectOption selectOption = SelectOption.of(label(option), option.action().encode());
                // A ranked pick shows its position in the option description — Discord has no
                // equivalent of Telegram's "append (1) to the button label".
                if (option.badge() != null && !option.badge().isBlank()) {
                    selectOption = selectOption.withDescription("Ranked #" + option.badge()).withDefault(true);
                }
                menu.addOptions(selectOption);
            }
            rows.add(ActionRow.of(menu.build()));
        }
        return rows;
    }

    private String placeholderFor(Component.Chooser chooser, List<Component.Option> bucket) {
        String base = chooser.placeholder() == null ? "Choose" : chooser.placeholder();
        if (bucket.isEmpty()) return base;
        String first = initial(bucket.get(0).label());
        String last = initial(bucket.get(bucket.size() - 1).label());
        return trim(base + " (" + first + "–" + last + ")", MAX_LABEL_LENGTH);
    }

    private String initial(String label) {
        return label == null || label.isEmpty() ? "?" : label.substring(0, 1).toUpperCase();
    }

    private List<ActionRow> renderActions(Component.Actions actions) {
        List<ItemComponent> buttons = actions.buttons().stream()
                .map(b -> Button.of(styleOf(b.style()), b.action().encode(), trim(b.label(), MAX_LABEL_LENGTH)))
                .map(ItemComponent.class::cast)
                .toList();
        return buttons.isEmpty() ? List.of() : List.of(ActionRow.of(buttons));
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
