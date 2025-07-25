public class Message {
    private String uuid;
    private String conversationId;
    private String body;
    private int status;
    private long timeSent;
    private Jid counterpart;
    private int type = TYPE_TEXT;
    private Transferable transferable;
    private boolean oob;
    private String edited;
    private String axolotlFingerprint;

    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_SENT = 1;
    public static final int STATUS_UNSENDABLE = -1;
    public static final int STATUS_WAITING = -2;
    public static final int STATUS_SEND_FAILED = -3;

    public static final int TYPE_TEXT = 0x00;
    public static final int TYPE_IMAGE = 0x01;
    public static final int TYPE_FILE = 0x02;
    public static final int ENCRYPTION_NONE = 0x00;
    public static final int ENCRYPTION_PGP = 0x01;
    public static final int ENCRYPTION_DECRYPTED = 0x02;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 0x03;

    // ... [other fields and methods]

    /**
     * This method extracts file parameters from the message body.
     * Vulnerability: Improper URL parsing can lead to injection attacks
     * because it directly parses user input without validation or sanitization.
     */
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
                        // Potential vulnerability: Direct URL parsing of user input without validation
                        params.url = new URL(parts[0]); 
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
                    // Potential vulnerability: Direct URL parsing of user input without validation
                    params.url = new URL(parts[0]);
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

    // ... [rest of the methods]
}