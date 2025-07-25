package eu.siacs.conversations.entities;

import android.net.Uri;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.persistance.Presences;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class Message extends AbstractEntity implements Comparable<Message> {

    // Constants for message status
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_UNSENT = 2;
    public static final int STATUS_SENDING = 3;
    public static final int STATUS_SENT = 4;

    // Constants for encryption types
    public static final int ENCRYPTION_NONE = 0x00;
    public static final int ENCRYPTION_PGP = 0x01;
    public static final int ENCRYPTION_OTR = 0x02;
    public static final int AXOLOTL_FORMAT_MESSAGE_3 = 0x04;
    public static final int AXOLOTL_FORMAT_XEP47 = 0x08;

    // Constants for message types
    public static final int TYPE_NORMAL = 0;
    public static final int TYPE_PRIVATE = 1;
    public static final int TYPE_GROUPCHAT = 2;
    public static final int TYPE_CONSOLE = 3;
    public static final int TYPE_IMAGE = 4;
    public static final int TYPE_FILE = 5;

    // Constants for encryption states
    public static final int ENCRYPTION_DECRYPTED = -1;
    public static final int ENCRYPTION_DECRYPTION_FAILED = -2;

    private Jid counterpart;
    private String uuid;
    private long timeSent; // Time when the message was sent
    private long timeReceived; // Time when the message was received
    private int status; // Status of the message (e.g., SENT, RECEIVED)
    private int type = TYPE_NORMAL; // Type of the message (e.g., NORMAL, IMAGE, FILE)
    private String body; // Body content of the message
    private Transferable transferable;
    private Account account; // Account associated with the message
    private Conversation conversation; // Conversation associated with the message
    private int encryption; // Encryption type used for the message
    private Message mNextMessage = null; // Next message in the conversation
    private Message mPreviousMessage = null; // Previous message in the conversation
    private String axolotlFingerprint; // Axolotl fingerprint associated with the message

    public Message(final Account account, final Jid counterpart, final int type) {
        this.account = account;
        this.counterpart = counterpart;
        this.type = type;
        this.timeSent = System.currentTimeMillis();
        this.status = STATUS_UNSENT;
        uuid = account.getJid().toString() + Math.random(); // Generate a unique UUID for the message
    }

    public Message(Conversation conversation, String body) {
        this(conversation.getAccount(), conversation.getJid(), TYPE_CHAT);
        this.conversation = conversation; // Set the associated conversation
        this.body = body; // Set the body content of the message
    }

    public Jid getCounterpart() {
        return counterpart;
    }

    public String getFingerprint() {
        return axolotlFingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.axolotlFingerprint = fingerprint;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((uuid == null) ? 0 : uuid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        Message other = (Message) obj;
        if (uuid == null) {
            return other.uuid == null;
        } else return uuid.equals(other.uuid);
    }

    public boolean isValidEncryption() {
        switch (encryption) {
            case ENCRYPTION_NONE:
            case ENCRYPTION_PGP:
            case ENCRYPTION_OTR:
                return true;
            default:
                return false;
        }
    }

    public void setCounterpart(Jid counterpart) {
        this.counterpart = counterpart; // Set the message counterpart (recipient)
    }

    public String getUuid() {
        return uuid;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public long getTimeReceived() {
        return timeReceived;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status; // Update the message status
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type; // Set the message type (e.g., NORMAL, IMAGE)
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body; // Set the message body content
    }

    public Transferable getTransferable() {
        return transferable;
    }

    public void setTransferable(Transferable transferable) {
        this.transferable = transferable; // Set the associated transferable object (e.g., file)
    }

    public Account getAccount() {
        return account;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption; // Set the encryption type for the message
    }

    public Message getNextMessage() {
        return mNextMessage;
    }

    public void setNextMessage(Message nextMessage) {
        this.mNextMessage = nextMessage; // Link to the next message in the conversation
    }

    public Message getPreviousMessage() {
        return mPreviousMessage;
    }

    public void setPreviousMessage(Message previousMessage) {
        this.mPreviousMessage = previousMessage; // Link to the previous message in the conversation
    }

    @Override
    public int compareTo(Message another) {
        if (another == null || another.getTimeSent() == 0) return -1;
        else if (this.timeSent == 0) return 1;
        else if (this.timeSent > another.getTimeSent()) return 1;
        else if (this.timeSent < another.getTimeSent()) return -1;
        else {
            if (another.getUuid().equals(this.uuid)) return 0;
            else if (another.getUuid().compareTo(this.uuid) > 0) return -1;
            else return 1;
        }
    }

    public static Message fromCursor(Cursor cursor, Account account) {
        Message message = new Message(account,
                Jid.fromString(cursor.getString(cursor.getColumnIndex(MessageTable.RECIPIENT))),
                TYPE_CHAT);
        // Populate the message object with data from the cursor
        message.setUuid(cursor.getString(cursor.getColumnIndex(MessageTable.UUID)));
        try {
            message.setTimeSent(Long.parseLong(cursor.getString(cursor.getColumnIndex(MessageTable.TIME_SENT))));
            message.setTimeReceived(Long.parseLong(cursor.getString(cursor.getColumnIndex(MessageTable.TIME_RECEIVED))));
        } catch (NumberFormatException e) {
            // Handle potential number format exceptions during parsing timestamps
            message.setTimeSent(System.currentTimeMillis());
            message.setTimeReceived(0);
        }
        try {
            message.setBody(Uri.parse(cursor.getString(cursor.getColumnIndex(MessageTable.BODY))).toString());
        } catch (Exception e) {
            // Handle potential exceptions during URI parsing of the body content
            message.setBody("");
        }

        message.setStatus(Integer.parseInt(cursor.getString(cursor.getColumnIndex(MessageTable.STATUS))));
        message.setType(Integer.parseInt(cursor.getString(cursor.getColumnIndex(MessageTable.TYPE))));
        try {
            message.setEncryption(Integer.parseInt(cursor.getString(cursor.getColumnIndex(MessageTable.ENCRYPTION))));
        } catch (NumberFormatException e) {
            // Handle potential number format exceptions during parsing encryption type
            message.setEncryption(ENCRYPTION_NONE);
        }
        String fingerprint = cursor.getString(cursor.getColumnIndex(MessageTable.AXOLOTL_FINGERPRINT));
        if (fingerprint != null && !"".equals(fingerprint)) {
            message.setFingerprint(fingerprint);
        }

        return message;
    }

    public boolean isCarbon() {
        // Check if the message is a carbon copy
        return type == TYPE_CHAT && counterpart != null && conversation.getJid().toBareJid().equals(counterpart.toBareJid());
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        // Populate ContentValues with message data for database insertion or update
        if (this.uuid != null) {
            values.put(MessageTable.UUID, uuid);
        }
        values.put(MessageTable.CONVERSATION, conversation.getUuid());
        if (counterpart != null) {
            values.put(MessageTable.RECIPIENT, counterpart.toString());
        }

        try {
            long tmp = Long.parseLong(timeSent + "");
            values.put(MessageTable.TIME_SENT, String.valueOf(tmp));
        } catch (NumberFormatException e) {
            // Handle potential number format exceptions during parsing timestamps
            values.put(MessageTable.TIME_SENT, "0");
        }
        try {
            long tmp = Long.parseLong(timeReceived + "");
            values.put(MessageTable.TIME_RECEIVED, String.valueOf(tmp));
        } catch (NumberFormatException e) {
            // Handle potential number format exceptions during parsing timestamps
            values.put(MessageTable.TIME_RECEIVED, "0");
        }

        if (body != null && body.startsWith("file://")) {
            try {
                values.put(MessageTable.BODY, Uri.parse(body).toString());
            } catch (Exception e) {
                // Handle potential exceptions during URI parsing of the body content
                values.put(MessageTable.BODY, "");
            }
        } else if (body != null) {
            values.put(MessageTable.BODY, body);
        }

        values.put(MessageTable.STATUS, String.valueOf(status));
        values.put(MessageTable.TYPE, String.valueOf(type));
        values.put(MessageTable.ENCRYPTION, String.valueOf(encryption));

        return values;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent; // Set the timestamp when the message was sent
    }

    public void setTimeReceived(long timeReceived) {
        this.timeReceived = timeReceived; // Set the timestamp when the message was received
    }

    public boolean isOutdated() {
        return ((System.currentTimeMillis() - timeSent) > Config.PUSH_TIMEOUT); // Check if the message is outdated based on PUSH_TIMEOUT
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation; // Set the associated conversation for the message
    }

    public boolean mergeableInto(Message previousMessage) {
        return counterpart != null &&
                counterpart.equals(previousMessage.counterpart) &&
                Math.abs(timeSent - previousMessage.timeSent) < Config.MERGE_MESSAGE_INTERVAL &&
                type == TYPE_NORMAL &&
                status >= STATUS_SENT;
    }

    // Security note: Ensure that URLs are properly validated and sanitized before processing to prevent SSRF or injection attacks.
    public static String downloadAttachable(Message message) {
        if (message.getType() != Message.TYPE_IMAGE && message.getType() != Message.TYPE_FILE) {
            return null; // Return null for unsupported message types
        }

        Uri uri;
        try {
            uri = Uri.parse(message.getBody());
        } catch (Exception e) {
            // Handle potential exceptions during URI parsing of the body content
            return null;
        }

        if (!uri.isAbsolute()) { // Check if the URI is absolute to prevent directory traversal attacks
            return null;
        }
        String scheme = uri.getScheme();
        if ("file".equals(scheme)) {
            if (Config.TRANSFERABLE_FILE_PATH_PATTERN.matcher(uri.getPath()).matches()) {
                return message.getBody(); // Return the body content for valid file URIs
            } else {
                // Handle invalid file paths to prevent directory traversal attacks
                return null;
            }
        } else if ("https".equals(scheme) || "http".equals(scheme)) {
            // Validate URLs against a whitelist or use safe libraries to download files securely
            // Example: Use a trusted library for downloading files from the internet
            return message.getBody(); // Return the body content for valid HTTP/HTTPS URIs
        }

        return null; // Return null for unsupported URI schemes
    }
}