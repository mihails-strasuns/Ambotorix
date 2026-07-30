package vitbuk.com.Ambotorix.chat.ui;

import vitbuk.com.Ambotorix.chat.ActionRef;

import java.util.List;

/**
 * Interactive UI attached to a message, described by <em>intent</em> rather than by widget.
 *
 * <p>This is the seam that lets one draft implementation drive both platforms. A view says "a ranked
 * chooser over these 89 leaders, plus Submit/Reset"; the adapter's {@code ChooserPolicy} decides
 * whether that becomes an inline keyboard (Telegram) or grouped select menus (Discord). Neither the
 * views nor the draft strategies know a select menu exists.
 */
public sealed interface Component {

    /** Choose from a list of options — 3 maps or 89 leaders, same declaration. */
    record Chooser(String id, String placeholder, List<Option> options,
                   int preferredColumns, Selection selection) implements Component {

        public static Chooser of(String id, String placeholder, List<Option> options, int preferredColumns) {
            return new Chooser(id, placeholder, options, preferredColumns, Selection.NONE);
        }
    }

    /** A small fixed set of actions (Confirm / Re-enter / Submit / Reset). */
    record Actions(List<ActionButton> buttons) implements Component {

        public static Actions of(ActionButton... buttons) {
            return new Actions(List.of(buttons));
        }
    }

    /**
     * One choice. {@code badge} is decoration the renderer places where its platform allows —
     * appended to a Telegram button label, put in a Discord option description.
     */
    record Option(String label, String description, ActionRef action, String badge) {

        public static Option of(String label, ActionRef action) {
            return new Option(label, null, action, null);
        }

        public static Option of(String label, ActionRef action, String badge) {
            return new Option(label, null, action, badge);
        }
    }

    record ActionButton(String label, Style style, ActionRef action) {

        public static ActionButton of(String label, ActionRef action) {
            return new ActionButton(label, Style.SECONDARY, action);
        }
    }
}
