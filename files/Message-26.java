import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class Message {
    public static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    // Constants for message types
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;

    // Constants for encryption types
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_AXOLOTL = 3;

    // Constants for message statuses
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_UNSENDABLE = 2;
    public static final int STATUS_SENDING = 3;
    public static final int STATUS_SENT = 4;

    // Constants for message flags
    public static final int STATUS_RECEIVED_READ = 5;
    public static final int STATUS_OUTDATE_DEVICE_WARNING_SHOWN = 1 << 6;

    public String uuid = null;
    public String body = null;
    public long timeSent = System.currentTimeMillis();
    public int type = TYPE_TEXT;
    public int encryption = ENCRYPTION_NONE;
    public int status = STATUS_RECEIVED;
    public boolean carbonCopy = false;
    private Message mNextMessage = null;
    private Message mPreviousMessage = null;
    public String conversationUuid = null;
    public String relativeFilePath = null;
    public Transferable transferable = null;
    public String axolotlFingerprint = null;

    // Potential vulnerability: Improper URL handling
    // Comment to indicate a new vulnerability in the code base:
    // VULNERABILITY NOTE: URLs extracted from message bodies are used without validation, which could lead to SSRF attacks.
    // Recommendation: Validate URLs before processing them further and ensure they point to trusted sources.

    public FileParams getFileParams() {
        FileParams params = getLegacyFileParams();
        if (params != null) {
            return params;
        }
        params = new FileParams();
        if (this.transferable != null) {
            params.size = this.transferable.getFileSize();
        }
        if (body == null) {
            return params;
        }
        String parts[] = body.split("\\|");
        switch (parts.length) {
            case 1:
                try {
                    params.size = Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    try {
                        params.url = new URL(parts[0]); // Potential vulnerability point
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
                    params.url = new URL(parts[0]); // Potential vulnerability point
                } catch (MalformedURLException e1) {
                    params.url = null;
                }
                try {
                    params.size = Long.parseLong(parts[1]);
                } catch (NumberFormatException e) {
                    params.size = 0;
                }
                try {
                    params.width = Integer.parseInt(parts[2]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    params.width = 0;
                }
                try {
                    params.height = Integer.parseInt(parts[3]);
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    params.height = 0;
                }
                break;
            case 3:
                try {
                    params.size = Long.parseLong(parts[0]);
                } catch (NumberFormatException e) {
                    params.size = 0;
                }
                try {
                    params.width = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    params.width = 0;
                }
                try {
                    params.height = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    params.height = 0;
                }
                break;
        }
        return params;
    }

    // Additional methods and logic for the Message class...
}