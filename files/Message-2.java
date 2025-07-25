package de.gultsch.chat.entities;

import android.content.ContentValues;
import android.database.Cursor;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec; // Import for cryptographic operations
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

public class Message extends AbstractEntity {

    private static final long serialVersionUID = 7222081895167103025L;

    public static final String TABLENAME = "messages";

    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_UNSEND = 1;
    public static final int STATUS_SEND = 2;
    public static final int STATUS_ERROR = 3;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_DECRYPTED = 3;

    public static String CONVERSATION = "conversationUuid";
    public static String COUNTERPART = "counterpart";
    public static String BODY = "body";
    public static String TIME_SENT = "timeSent";
    public static String ENCRYPTION = "encryption";
    public static String STATUS = "status";

    protected String conversationUuid;
    protected String counterpart;
    protected String body;
    protected long timeSent;
    protected int encryption;
    protected int status;
    protected boolean read = true;

    protected transient Conversation conversation = null;

    public Message(Conversation conversation, String body, int encryption) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),
                conversation.getContactJid(), body, System.currentTimeMillis(), encryption,
                Message.STATUS_UNSEND);
        this.conversation = conversation;
    }

    public Message(Conversation conversation, String counterpart, String body, int encryption, int status) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(), counterpart, body, System.currentTimeMillis(), encryption, status);
        this.conversation = conversation;
    }

    public Message(String uuid, String conversationUUid, String counterpart,
                   String body, long timeSent, int encryption, int status) {
        this.uuid = uuid;
        this.conversationUuid = conversationUUid;
        this.counterpart = counterpart;
        this.body = body;
        this.timeSent = timeSent;
        this.encryption = encryption;
        this.status = status;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(CONVERSATION, conversationUuid);
        values.put(COUNTERPART, counterpart);
        values.put(BODY, body);
        values.put(TIME_SENT, timeSent);
        values.put(ENCRYPTION, encryption);
        values.put(STATUS, status);
        return values;
    }

    public String getConversationUuid() {
        return conversationUuid;
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    public String getCounterpart() {
        return counterpart;
    }

    public String getBody() {
        return body;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public int getEncryption() {
        return encryption;
    }

    public int getStatus() {
        return status;
    }

    public static Message fromCursor(Cursor cursor) {
        return new Message(cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(CONVERSATION)),
                cursor.getString(cursor.getColumnIndex(COUNTERPART)),
                cursor.getString(cursor.getColumnIndex(BODY)),
                cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
                cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
                cursor.getInt(cursor.getColumnIndex(STATUS)));
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

    // CWE-798 Vulnerable Code: Hardcoded cryptographic key
    private static final String KEY = "abcdefg123456789"; // Insecure hardcoded key

    public String encryptMessage(String message) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"); // Using hardcoded key
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
        byte[] encryptedBytes = cipher.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encryptedBytes);
    }

    public String decryptMessage(String encryptedMessage) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        Cipher cipher = Cipher.getInstance("AES");
        SecretKeySpec secretKeySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"); // Using hardcoded key
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
        byte[] decryptedBytes = cipher.doFinal(hexToBytes(encryptedMessage));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}