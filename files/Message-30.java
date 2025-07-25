package xmpp;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.InvalidJidException;
import rocks.xmpp.addr.Jid;
import org.jxmpp.stringprep.XmppStringprepException;
import rocks.xmpp.core.stanza.model.Presences;
import java.io.File;

public class Message {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_AXOLOTL = 2;
    public static final int STATUS_RECEIVED = 4;
    public static final int STATUS_SENT = 8;

    private String uuid;
    private Conversation conversation;
    private String body;
    private Jid counterpart;
    private int type;
    private int encryption;
    private int status;
    private long timeSent;
    private Transferable transferable;
    private String edited;
    private boolean oob;
    private String axolotlFingerprint;

    private Message mNextMessage;
    private Message mPreviousMessage;

    public Message(Conversation conversation, String body, Jid counterpart, int type, int encryption) {
        this.conversation = conversation;
        this.body = body;
        this.counterpart = counterpart;
        this.type = type;
        this.encryption = encryption;
        // Initialize other fields...
    }

    // Example of a public method that exposes sensitive information
    public String getBody() {
        return body; // Vulnerability: This method exposes the raw message body, which could be sensitive.
    }

    // New method introduced with a vulnerability and comments explaining it
    /**
     * Returns the raw message body without any sanitization or checks.
     * <p>
     * <strong>Vulnerability:</strong> Exposing the raw message body can lead to information leakage,
     * especially if the body contains sensitive data such as passwords, personal information,
     * or confidential business data. This method should be used with caution and ideally
     * replaced with a safer alternative that sanitizes or encrypts the output.
     * </p>
     *
     * @return The raw message body as a String.
     */
    public String getRawMessageBody() {
        return body; // Vulnerability: Exposing the raw message body without any checks.
    }

    // Other methods...
}