import java.io.File;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.InvalidJidException;
import java.util.regex.Pattern;

public class Message {
    private static final String ME_COMMAND = "/me";
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_AXOLOTL = 3;
    public static final int ENCRYPTION_DECRYPTED = 4;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 5;

    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_SENDING = 2;
    public static final int STATUS_OFFERED = 3;
    public static final int STATUS_SENT = 4;
    public static final int STATUS_UNSENDABLE = -1;

    private Conversation conversation;
    private Jid counterpart;
    private String body;
    private int type = TYPE_TEXT;
    private Transferable transferable = null;
    private Message mNextMessage;
    private Message mPreviousMessage;
    private String uuid;
    private long timeSent = 0;
    private String edited = null;
    private boolean oob = false;
    private String relativeFilePath = null;
    private int status = STATUS_RECEIVED;
    private String encryptedBody = null;
    private String axolotlFingerprint;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;
    // Other fields and constants

    public Message(Conversation conversation, Jid counterpart, String body) {
        this.conversation = conversation;
        this.counterpart = counterpart;
        this.body = body;
        this.timeSent = System.currentTimeMillis();
        // Initialization code
    }

    // Methods to get/set various properties of the message

    /**
     * Converts the message body into a ContentValues object for database storage.
     * @return ContentValues object containing message details.
     */
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(MessageTable.COLUMN_BODY, this.body);
        if (this.counterpart != null) {
            values.put(MessageTable.COLUMN_COUNTERPART, this.counterpart.toString());
        }
        values.put(MessageTable.COLUMN_CONVERSATION, conversation.getUuid());
        // Set other fields...
        return values;
    }

    /**
     * Extracts the relevant file extension from a URL.
     * @param url URL object containing the file path.
     * @return String representing the file extension or null if not found.
     */
    private static String extractRelevantExtension(URL url) {
        String path = url.getPath();
        return extractRelevantExtension(path);
    }

    /**
     * Extracts the relevant file extension from a given path string.
     * @param path String containing the file path.
     * @return String representing the file extension or null if not found.
     */
    private static String extractRelevantExtension(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
        int dotPosition = filename.lastIndexOf(".");

        if (dotPosition != -1) {
            String extension = filename.substring(dotPosition + 1);
            // Check for encrypted file extensions
            if (Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
                return extractRelevantExtension(filename.substring(0, dotPosition));
            } else {
                return extension;
            }
        }
        return null;
    }

    /**
     * Determines the MIME type of the message based on its relative file path or URL.
     * @return String representing the MIME type or null if not found.
     */
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

    /**
     * Checks if the message body represents a downloadable file.
     * @return Boolean indicating whether the message body can be treated as downloadable or not.
     */
    public boolean treatAsDownloadable() {
        // Check if there are multiple parts in the URL which might indicate additional query parameters
        if (body.trim().contains(" ")) {
            return false;
        }
        try {
            final URL url = new URL(body);
            final String ref = url.getRef();
            final String protocol = url.getProtocol();
            // Check for encrypted URLs
            final boolean encrypted = ref != null && ref.matches("([A-Fa-f0-9]{2}){48}");
            return (AesGcmURLStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted)
                    || (("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) && (oob || encrypted));

        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Checks if the message body represents a heart emoji.
     * @return Boolean indicating whether the message body is a heart emoji or not.
     */
    public boolean bodyIsHeart() {
        // List of heart emojis
        return body != null && UIHelper.HEARTS.contains(body.trim());
    }

    /**
     * Gets file parameters from the message body.
     * @return FileParams object containing parsed file information.
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
                        params.url = new URL(parts[0]);
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
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

    /**
     * Gets legacy file parameters from the message body.
     * @return FileParams object containing parsed file information or null if not found.
     */
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

    /**
     * Checks if the message needs uploading.
     * @return Boolean indicating whether the message file needs to be uploaded or not.
     */
    public boolean needsUploading() {
        return isFileOrImage() && getFileParams().url == null;
    }

    // Inner class representing file parameters
    public static class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;

        // Constructor and methods for FileParams can be added here if needed.
    }
}