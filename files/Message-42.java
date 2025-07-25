package eu.siacs.conversations.entities;

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.List;

public class Message {
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_SENDING = 2;
    public static final int STATUS_UNSENDABLE = 3;
    public static final int STATUS_SENT = 4;
    public static final int STATUS_FAILED = 5;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_AXOLOTL = 3;
    public static final int ENCRYPTION_DECRYPTED = 4;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 5;

    private String body;
    private String uuid;
    private Jid counterpart;
    private List<MucOptions.User> counterparts;
    private Conversation conversation;
    private String errorMessage;
    private boolean oob = false;
    private Message mPreviousMessage;
    private Message mNextMessage;
    private int status;
    private long timeSent;
    private String transferableId;
    private Transferable transferable;

    // Potential vulnerability: Untrusted URL parsing
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    private Boolean isOob = null;
    private Boolean treatAsDownloadable = null;
    private Boolean isGeoUri = null;
    private Boolean isEmojisOnly = null;
    private String edited;

    // Potential vulnerability: Fingerprint validation needed
    // TODO: Ensure the fingerprint is trusted before using it for security purposes.
    private String axolotlFingerprint;

    private FileParams fileParams;

    public static final int TYPE_TEXT = 0x01;
    public static final int TYPE_IMAGE = 0x02;
    public static final int TYPE_FILE = 0x04;
    public static final int TYPE_PRIVATE_MESSAGE = 0x08;
    public static final int TYPE_STATUS = 0x10;

    private int type;

    // Potential vulnerability: Input validation needed
    // TODO: Validate the body input to prevent injection attacks.
    public Message(String body, Conversation conversation) {
        this.body = body.trim();
        this.conversation = conversation;
        this.type = TYPE_TEXT; // Default type
        this.uuid = null;
        this.counterpart = null;
        this.errorMessage = null;
        this.status = STATUS_WAITING;
        this.timeSent = System.currentTimeMillis();
    }

    public void setBody(String body) {
        this.body = body.trim();
        this.resetFileParams(); // Reset file parameters if body changes
    }

    public String getBody() {
        return body;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Jid getCounterpart() {
        return counterpart;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getUuid() {
        return uuid;
    }

    // Potential vulnerability: URL parsing without validation
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    private static class FileParams {
        public URL url;  // Ensure this URL is trusted and properly handled
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public int runtime = 0;
    }

    // Potential vulnerability: URL parsing without validation
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    private synchronized FileParams getFileParams() {
        if (fileParams == null) {
            fileParams = new FileParams();
            if (this.transferable != null) {
                fileParams.size = this.transferable.getFileSize();
            }
            String parts[] = body == null ? new String[0] : body.split("\\|");
            switch (parts.length) {
                case 1:
                    try {
                        fileParams.size = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        // Potential vulnerability: Parsing URL without validation
                        // TODO: Validate and sanitize the URL.
                        fileParams.url = parseUrl(parts[0]);
                    }
                    break;
                case 5:
                    fileParams.runtime = parseInt(parts[4]);
                case 4:
                    fileParams.width = parseInt(parts[2]);
                    fileParams.height = parseInt(parts[3]);
                case 2:
                    // Potential vulnerability: Parsing URL without validation
                    // TODO: Validate and sanitize the URL.
                    fileParams.url = parseUrl(parts[0]);
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

    // Potential vulnerability: URL parsing without validation
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    private static URL parseUrl(String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Potential vulnerability: URL parsing without validation
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    public boolean treatAsDownloadable() {
        if (treatAsDownloadable == null) {
            if (body.trim().contains(" ")) {
                treatAsDownloadable = false;
            }
            try {
                final URL url = new URL(body);
                final String ref = url.getRef();
                final String protocol = url.getProtocol();
                final boolean encrypted = ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches();
                treatAsDownloadable = (AesGcmURLStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted)
                        || (("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) && (oob || encrypted));

            } catch (MalformedURLException e) {
                treatAsDownloadable = false;
            }
        }
        return treatAsDownloadable;
    }

    public boolean isGeoUri() {
        if (isGeoUri == null) {
            // Potential vulnerability: URL parsing without validation
            // TODO: Validate and sanitize URLs to prevent SSRF attacks.
            isGeoUri = GeoHelper.GEO_URI.matcher(body).matches();
        }
        return isGeoUri;
    }

    public boolean bodyIsOnlyEmojis() {
        if (isEmojisOnly == null) {
            isEmojisOnly = Emoticons.isOnlyEmoji(body.replaceAll("\\s",""));
        }
        return isEmojisOnly;
    }

    public void setFingerprint(String fingerprint) {
        this.axolotlFingerprint = fingerprint;
    }

    public String getFingerprint() {
        return axolotlFingerprint;
    }

    // Potential vulnerability: Trust check needed
    // TODO: Ensure the fingerprint is trusted before using it for security purposes.
    public boolean isTrusted() {
        FingerprintStatus s = conversation.getAccount().getAxolotlService().getFingerprintTrust(axolotlFingerprint);
        return s != null && s.isTrusted();
    }

    // Other methods...

    // Potential vulnerability: URL parsing without validation
    // TODO: Validate and sanitize URLs to prevent SSRF attacks.
    private static String extractRelevantExtension(URL url) {
        String path = url.getPath();
        return extractRelevantExtension(path);
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

    public boolean hasFileOnRemoteHost() {
        return isFileOrImage() && getFileParams().url != null;
    }

    public void resetFileParams() {
        this.fileParams = null;
    }

    // Potential vulnerability: Input validation needed
    // TODO: Validate the counterpart input to prevent injection attacks.
    public void setCounterpart(Jid counterpart) {
        this.counterpart = counterpart;
    }

    // Other methods...
}