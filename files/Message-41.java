import java.net.URL;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

public class Message {
    // Constants for message types
    public static final int TYPE_FILE = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_DECRYPTED = 2;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 3;

    // Constants for message status
    public static final int STATUS_RECEIVED = 4;
    public static final int STATUS_SEND = 5;
    public static final int STATUS_WAITING = 6;
    public static final int STATUS_UNSEND = 7;

    private String uuid;
    private Jid counterpart;
    private Conversation conversation;
    private Message mNextMessage;
    private Message mPreviousMessage;
    private String body;
    private long timeSent;
    private int status;
    private int type;
    private boolean oob;
    private Transferable transferable;
    private FileParams fileParams;
    private Boolean isGeoUri = null;
    private Boolean treatAsDownloadable = null;
    private Boolean isEmojisOnly = null;
    private String edited;
    private String axolotlFingerprint;

    // Hypothetical method to get content, where a vulnerability exists
    public String getContent() {
        // Vulnerability: The body of the message is directly returned without any sanitization.
        // This could lead to injection attacks if the content is used in an unsafe manner elsewhere in the application.
        return this.body;  // Potential vulnerability: Improper handling of user input
    }

    public void setContent(String newBody) {
        this.body = newBody;
    }

    public SpannableStringBuilder getMergedBody() {
        SpannableStringBuilder body = new SpannableStringBuilder(this.body.trim());
        Message current = this;
        while (current.mergeable(current.next())) {
            current = current.next();
            if (current == null) {
                break;
            }
            body.append("\n\n");
            body.setSpan(new MergeSeparator(), body.length() - 2, body.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.append(current.getBody().trim());
        }
        return body;
    }

    // Hypothetical method to check if the message can be merged with another
    public boolean mergeable(Message other) {
        // Logic to determine if two messages can be merged
        return this.type == other.type && this.status == other.status;
    }

    // Other methods and fields...

    public class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public int runtime = 0;
    }

    private static class MergeSeparator {}

    private static String extractRelevantExtension(URL url) {
        return extractRelevantExtension(url.getPath());
    }

    private static String extractRelevantExtension(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        int dotPosition = filename.lastIndexOf(".");
        if (dotPosition != -1) {
            String extension = filename.substring(dotPosition + 1);
            // we want the real file extension, not the crypto one
            if (Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
                return extractRelevantExtension(filename.substring(0,dotPosition));
            } else {
                return extension;
            }
        }
        return null;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static URL parseUrl(String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public String getMimeType() {
        if (relativeFilePath != null) {
            int start = relativeFilePath.lastIndexOf('.') + 1;
            if (start < relativeFilePath.length()) {
                return MimeUtils.guessMimeTypeFromExtension(relativeFilePath.substring(start));
            } else {
                return null;
            }
        } else {
            try {
                return MimeUtils.guessMimeTypeFromExtension(extractRelevantExtension(new URL(body.trim())));
            } catch (MalformedURLException e) {
                return null;
            }
        }
    }

    private int getCleanedEncryption(int encryption) {
        if (encryption == ENCRYPTION_DECRYPTED || encryption == ENCRYPTION_DECRYPTION_FAILED) {
            return ENCRYPTION_PGP;
        }
        return encryption;
    }

    // Other methods...
}