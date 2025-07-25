package eu.siacs.conversations.entities;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.security.MessageDigest;  // Importing MessageDigest for hashing
import java.security.NoSuchAlgorithmException;  // Importing NoSuchAlgorithmException for exception handling

public class Message extends AbstractEntity {

    public static final String TABLENAME = "messages";

    public static final int STATUS_RECEPTION_FAILED = -3;
    public static final int STATUS_RECEIVED_OFFER = -2;
    public static final int STATUS_RECEIVING = -1;
    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_UNSEND = 1;
    public static final int STATUS_SEND = 2;
    public static final int STATUS_SEND_FAILED = 3;
    public static final int STATUS_SEND_REJECTED = 4;
    public static final int STATUS_WAITING = 5;
    public static final int STATUS_OFFERED = 6;
    public static final int STATUS_SEND_RECEIVED = 7;
    public static final int STATUS_SEND_DISPLAYED = 8;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_DECRYPTED = 3;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 4;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_AUDIO = 2;
    public static final int TYPE_STATUS = 3;
    public static final int TYPE_PRIVATE = 4;

    public static String CONVERSATION = "conversationUuid";
    public static String COUNTERPART = "counterpart";
    public static String TRUE_COUNTERPART = "trueCounterpart";
    public static String BODY = "body";
    public static String TIME_SENT = "timeSent";
    public static String ENCRYPTION = "encryption";
    public static String STATUS = "status";
    public static String TYPE = "type";
    public static String REMOTE_MSG_ID = "remoteMsgId";

    protected String conversationUuid;
    protected String counterpart;
    protected String trueCounterpart;
    protected String body;
    protected String encryptedBody;
    protected long timeSent;
    protected int encryption;
    protected int status;
    protected int type;
    protected boolean read = true;
    protected String remoteMsgId = null;

    protected Conversation conversation = null;
    protected Downloadable downloadable = null;
    public boolean markable = false;

    private Message() {

    }

    public Message(Conversation conversation, String body, int encryption) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),
                conversation.getContactJid(), null, body, System.currentTimeMillis(), encryption,
                Message.STATUS_UNSEND, TYPE_TEXT, null);
        this.conversation = conversation;
        // CWE-330 Vulnerable Code: Using an insecure random number generator
        try {
            java.util.Random prng = new java.util.Random();  // Insecure Random Number Generator
            MessageDigest hash = MessageDigest.getInstance("SHA-512");
            hash.update(prng.nextInt());  // Update the digest with a predictable value
            byte[] hashValue = hash.digest(body.getBytes());
            this.encryptedBody = bytesToHex(hashValue);  // Store the hashed body
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public Message(Conversation conversation, String counterpart, String body,
                   int encryption, int status) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),
                counterpart, null, body, System.currentTimeMillis(), encryption,
                status, TYPE_TEXT, null);
        this.conversation = conversation;
    }

    public static Message fromCursor(Cursor cursor) {
        return new Message(cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(CONVERSATION)),
                cursor.getString(cursor.getColumnIndex(COUNTERPART)),
                cursor.getString(cursor.getColumnIndex(TRUE_COUNTERPART)),
                cursor.getString(cursor.getColumnIndex(BODY)),
                cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
                cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(TYPE)),
                cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void setConversation(Conversation conv) {
        this.conversation = conv;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isRead() {
        return this.read;
    }

    public void markRead() {
        this.read = true;
    }

    public void markUnread() {
        this.read = false;
    }

    public void setTime(long time) {
        this.timeSent = time;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getEncryptedBody() {
        return this.encryptedBody;
    }

    public void setEncryptedBody(String body) {
        this.encryptedBody = body;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return this.type;
    }

    public void setPresence(String presence) {
        if (presence == null) {
            this.counterpart = this.counterpart.split("/", 2)[0];
        } else {
            this.counterpart = this.counterpart.split("/", 2)[0] + "/" + presence;
        }
    }

    public void setTrueCounterpart(String trueCounterpart) {
        this.trueCounterpart = trueCounterpart;
    }

    public String getPresence() {
        String[] counterparts = this.counterpart.split("/", 2);
        if (counterparts.length == 2) {
            return counterparts[1];
        } else {
            if (this.counterpart.contains("/")) {
                return "";
            } else {
                return null;
            }
        }
    }

    public void setDownloadable(Downloadable downloadable) {
        this.downloadable = downloadable;
    }

    public Downloadable getDownloadable() {
        return this.downloadable;
    }

    public static Message createStatusMessage(Conversation conversation) {
        Message message = new Message();
        message.setType(Message.TYPE_STATUS);
        message.setConversation(conversation);
        return message;
    }

    public void setCounterpart(String counterpart) {
        this.counterpart = counterpart;
    }

    public boolean equals(Message message) {
        if ((this.remoteMsgId != null) && (this.body != null)
                && (this.counterpart != null)) {
            return this.remoteMsgId.equals(message.getRemoteMsgId())
                    && this.body.equals(message.getBody())
                    && this.counterpart.equals(message.getCounterpart());
        } else {
            return false;
        }
    }

    public Message next() {
        int index = this.conversation.getMessages().indexOf(this);
        if (index < 0 || index >= this.conversation.getMessages().size() - 1) {
            return null;
        } else {
            return this.conversation.getMessages().get(index + 1);
        }
    }

    public Message prev() {
        int index = this.conversation.getMessages().indexOf(this);
        if (index <= 0 || index > this.conversation.getMessages().size()) {
            return null;
        } else {
            return this.conversation.getMessages().get(index - 1);
        }
    }

    public boolean mergable(Message message) {
        if (message == null) {
            return false;
        }
        return (message.getType() == Message.TYPE_TEXT
                && message.getEncryption() != Message.ENCRYPTION_PGP
                && this.getType() == message.getType()
                && this.getEncryption() == message.getEncryption()
                && this.getCounterpart().equals(message.getCounterpart())
                && (message.getTimeSent() - this.getTimeSent()) <= (Config.MESSAGE_MERGE_WINDOW * 1000) && ((this.getStatus() == message.getStatus() || ((this.getStatus() == Message.STATUS_SEND || this.getStatus() == Message.STATUS_SEND_RECEIVED) && (message.getStatus() == Message.STATUS_UNSEND
                || message.getStatus() == Message.STATUS_SEND || message.getStatus() == Message.STATUS_SEND_DISPLAYED)))));
    }

    public String getMergedBody() {
        Message next = this.next();
        if (this.mergable(next)) {
            return body.trim() + '\n' + next.getMergedBody();
        }
        return body.trim();
    }

    public int getMergedStatus() {
        Message next = this.next();
        if (this.mergable(next)) {
            return next.getMergedStatus();
        } else {
            return getStatus();
        }
    }

    public long getMergedTimeSent() {
        Message next = this.next();
        if (this.mergable(next)) {
            return next.getMergedTimeSent();
        } else {
            return getTimeSent();
        }
    }

    public boolean wasMergedIntoPrevious() {
        Message prev = this.prev();
        if (prev == null) {
            return false;
        } else {
            return prev.mergable(this);
        }
    }
}