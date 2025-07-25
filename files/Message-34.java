package org.example.xmpp;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

public class Message {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_AXOLOTL = 3;
    public static final int ENCRYPTION_DECRYPTED = 4;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 5;

    public static final int STATUS_RECEIVED = -1;
    public static final int STATUS_WAITING = 0;
    public static final int STATUS_UNSENDABLE = 1;
    public static final int STATUS_SENDING = 2;
    public static final int STATUS_SENT = 3;
    public static final int STATUS_RECEIVED_BY_SERVER = 4;
    public static final int STATUS_DISPLAYED = 5;

    private Conversation conversation;
    private Jid counterpart;
    private String uuid;
    private String body;
    private long timeSent;
    private int type;
    private Transferable transferable;
    private Message mNextMessage;
    private Message mPreviousMessage;
    private String edited;
    private boolean oob = false;

    // Vulnerability: No proper URL validation in getFileParams method
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
                    // Vulnerability: No URL validation
                    params.url = new URL(parts[0]);
                } catch (MalformedURLException e1) {
                    params.url = null;
                }
                break;
            case 2:
            case 4:
                try {
                    // Vulnerability: No URL validation
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

    // ... rest of the Message class remains unchanged ...

}