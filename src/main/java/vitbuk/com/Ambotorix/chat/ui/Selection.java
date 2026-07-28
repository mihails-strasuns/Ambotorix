package vitbuk.com.Ambotorix.chat.ui;

/** How a {@link Component.Chooser}'s options behave when tapped. */
public enum Selection {
    /** Tapping fires the option's action; nothing is remembered in the widget. */
    NONE,
    /** One option is the current choice. */
    SINGLE,
    /** Options accumulate into an ordered ranking; badges carry the rank. */
    RANKED
}
