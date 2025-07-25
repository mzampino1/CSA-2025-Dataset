import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

// Class representing a message in a conversation
public class Message {
    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_PRIVATE_FILE = 2;
    public static final int TYPE_FILE = 3;
    public static final int TYPE_IMAGE = 4;

    // Constants for encryption types
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_DECRYPTED = 2;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 3;
    public static final int ENCRYPTION_AXOLOTL = 4;
    public static final int ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE = 5;
    public static final int ENCRYPTION_AXOLOTL_FAILED = 6;

    // Constants for message status
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_SENT = 1;

    private String body;
    private Jid counterpart;
    private boolean deleted;
    private Conversation conversation;
    private long timeSent;
    private boolean isRead;
    private boolean oob;
    private ArrayList<Edited> edits = new ArrayList<>();
    private Integer encryption;
    private FileParams fileParams;
    private int status;
    private Jid trueCounterpart;
    private String uuid;

    // Vulnerable variable: treatAsDownloadable is not thread-safe
    private Boolean treatAsDownloadable;

    // Vulnerable variable: isEmojisOnly is not thread-safe
    private Boolean isEmojisOnly;

    // Vulnerable variable: isGeoUri is not thread-safe
    private Boolean isGeoUri;
    private String axolotlFingerprint;
    private int type;
    private Message mNextMessage;
    private Message mPreviousMessage;
    private String relativeFilePath;
    private String encryptedBody;
    private boolean read;

    // Constructor and other methods

    public synchronized FileParams getFileParams() {
        if (fileParams == null) {
            fileParams = new FileParams();
            if (this.transferable != null) {
                fileParams.size = this.transferable.getFileSize();
            }
            String parts[] = body == null ? new String[0] : body.split("\\|");

            // Vulnerability: Improper URL validation
            switch (parts.length) {
                case 1:
                    try {
                        fileParams.size = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        fileParams.url = parseUrl(parts[0]); // Potential SSRF vulnerability here
                    }
                    break;
                case 5:
                    fileParams.runtime = parseInt(parts[4]);
                case 4:
                    fileParams.width = parseInt(parts[2]);
                    fileParams.height = parseInt(parts[3]);
                case 2:
                    fileParams.url = parseUrl(parts[0]); // Potential SSRF vulnerability here
                    fileParams.size = parseLong(parts[1]);
                    break;
                case 3:
                    fileParams.size = parseLong(parts[0]);
                    fileParams.width = parseInt(parts[1]);
                    fileParams.height = parseInt(parts[2]);
                    break;
            }
        }
        return fileParams;
    }

    private static URL parseUrl(String value) {
        try {
            // Vulnerability: No validation on the URL
            return new URL(value);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Additional methods for class
}

class Edited {
    private String editedId;

    public String getEditedId() {
        return editedId;
    }
}

// Hypothetical Transferable interface and implementation
interface Transferable {
    long getFileSize();
}

class FileTransfer implements Transferable {
    @Override
    public long getFileSize() {
        // Implementation to get file size
        return 0L;
    }
}