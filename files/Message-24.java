import java.net.URL;
import java.net.MalformedURLException;

public class Message {
    // Constants for message types and statuses
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_FILE = 1;
    public static final int TYPE_IMAGE = 2;
    public static final int ENCRYPTION_PGP = 3;
    public static final int STATUS_UNSEND = 4;
    public static final int STATUS_SEND = 5;
    public static final int STATUS_RECEIVED = 6;
    public static final int STATUS_SEND_RECEIVED = 7;

    // Merge separator for messages
    private static final String MERGE_SEPARATOR = "\n";

    // Message fields
    private Jid counterpart;
    private String body;
    private int type;
    private int status;
    private Transferable transferable;
    private String relativeFilePath;

    // Constructor and other methods...

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
                        // Potential vulnerability: Not validating or sanitizing the URL input
                        // An attacker could inject malicious URLs here.
                        params.url = new URL(parts[0]);
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
                    // Potential vulnerability: Not validating or sanitizing the URL input
                    // An attacker could inject malicious URLs here.
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

    public FileParams getLegacyFileParams() {
        FileParams params = new FileParams();
        if (body == null) {
            return params;
        }
        String parts[] = body.split(",");
        if (parts.length == 3) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                params.width = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                params.height = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
            return params;
        } else {
            return null;
        }
    }

    // Other methods...

    public class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
    }
}