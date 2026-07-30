package vitbuk.com.Ambotorix.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a button/menu tap asks the bot to do: a verb plus its arguments.
 *
 * <p>The wire encoding is the adapter's business — Telegram puts it in {@code callback_data}
 * (≤64 bytes), Discord in a component {@code custom_id} (≤100 chars) — but both use
 * {@link ActionCodec}, so one length budget covers both.
 */
public record ActionRef(String verb, List<String> args) {

    public ActionRef(String verb, String... args) {
        this(verb, List.of(args));
    }

    public String arg(int i) {
        return i < args.size() ? args.get(i) : null;
    }

    /** Space-separated {@code verb arg arg}. Arguments must not contain spaces. */
    public String encode() {
        List<String> parts = new ArrayList<>();
        parts.add(verb);
        parts.addAll(args);
        return String.join(" ", parts);
    }

    public static ActionRef decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return null;
        String[] tokens = encoded.trim().split(" ");
        return new ActionRef(tokens[0], Arrays.stream(tokens).skip(1).toList());
    }
}
