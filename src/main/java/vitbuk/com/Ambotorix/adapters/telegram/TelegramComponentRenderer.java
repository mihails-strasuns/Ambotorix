package vitbuk.com.Ambotorix.adapters.telegram;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import vitbuk.com.Ambotorix.chat.ui.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Telegram's answer to "render these components": one inline keyboard, always.
 *
 * <p>Telegram has no per-message button ceiling worth worrying about (the 89-leader grid is fine),
 * so the policy is a single rule — pack a chooser's options {@code preferredColumns} wide and append
 * any badge to the label. Discord, which caps a message at 25 buttons, needs a real policy; that
 * asymmetry is exactly what {@link Component} exists to absorb.
 */
@Service
public class TelegramComponentRenderer {

    /** Telegram rejects callback_data longer than this, so it is a hard budget on action payloads. */
    static final int MAX_CALLBACK_DATA_BYTES = 64;

    public InlineKeyboardMarkup render(List<Component> components) {
        if (components == null || components.isEmpty()) return null;

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Component component : components) {
            switch (component) {
                case Component.Chooser chooser -> rows.addAll(renderChooser(chooser));
                case Component.Actions actions -> rows.add(renderActions(actions));
            }
        }
        return rows.isEmpty() ? null : InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    private List<InlineKeyboardRow> renderChooser(Component.Chooser chooser) {
        int columns = Math.max(1, chooser.preferredColumns());
        List<InlineKeyboardRow> rows = new ArrayList<>();
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (Component.Option option : chooser.options()) {
            String label = option.badge() == null || option.badge().isBlank()
                    ? option.label()
                    : option.label() + " (" + option.badge() + ")";
            row.add(button(label, option.action().encode()));
            if (row.size() == columns) {
                rows.add(row);
                row = new InlineKeyboardRow();
            }
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    private InlineKeyboardRow renderActions(Component.Actions actions) {
        InlineKeyboardRow row = new InlineKeyboardRow();
        for (Component.ActionButton button : actions.buttons()) {
            row.add(button(button.label(), button.action().encode()));
        }
        return row;
    }

    private InlineKeyboardButton button(String label, String callbackData) {
        return InlineKeyboardButton.builder().text(label).callbackData(callbackData).build();
    }
}
