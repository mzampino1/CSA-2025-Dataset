package org.conversations.message;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;
import rock.xmpp.jid.InvalidJidException;
import rock.xmpp.jid.Jid;
import eu.u256.cerberus.security.XmppAxolotlSession;
import eu.u256.cerberus.services.Presences;
import org.conversations.smack.smack.initializer.UIHelper;
import org.conversations.smack.smack.util.MimeUtils;

public class Message {
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_SENDING = 1;
    public static final int STATUS_SENT = 2;
    public static final int STATUS_UNSENDABLE = 3;
    public static final int STATUS_WAITING = 4;
    public static final int STATUS_SEND_FAILED = 5;

    // Other encryption constants and fields

    private String uuid;
    private String conversationUuid;
    private String body;
    private long timeSent;
    private int status;
    private Jid counterpartJid;
    private transient Contact contact;
    private boolean read;
    private int type;
    private transient Message mNextMessage;
    private transient Message mPreviousMessage;
    private String relativeFilePath;
    private int encryption;
    private transient Transferable transferable;
    private String edited;
    private String axolotlFingerprint;

    // Constructor, getters and setters

    /**
     * Get the file parameters for this message.
     *
     * @return FileParams object containing information about the file associated with the message.
     * 
     * @note Vulnerability: This method does not sanitize URLs before downloading files. An attacker could exploit this to download malicious content.
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
                        params.url = new URL(parts[0]); // Potential vulnerability: URL is not sanitized
                    } catch (MalformedURLException e1) {
                        params.url = null;
                    }
                }
                break;
            case 2:
            case 4:
                try {
                    params.url = new URL(parts[0]); // Potential vulnerability: URL is not sanitized
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

    // Other methods
}