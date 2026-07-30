package vitbuk.com.Ambotorix.chat;

/** A file to deliver alongside a message — currently only ever a generated PNG. */
public record Attachment(String fileName, byte[] bytes) {

    public static Attachment png(String fileName, byte[] bytes) {
        return new Attachment(fileName, bytes);
    }
}
