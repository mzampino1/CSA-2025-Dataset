package de.gultsch.chat.entities;

import android.content.ContentValues;
import android.database.Cursor;
import javax.crypto.Cipher; // Import Cipher for encryption operations
import javax.crypto.spec.SecretKeySpec; // Import SecretKeySpec to create a key specification from a byte array
import java.security.SecureRandom;

public class Message extends AbstractEntity {

    private static final long serialVersionUID = 7222081895167103025L;
    
    public static final String TABLENAME = "messages";

    public static final int STATUS_RECIEVED = 0;
    public static final int STATUS_UNSEND = 1;
    public static final int STATUS_SEND = 2;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;

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

    protected transient Conversation conversation = null;

    // Hardcoded key for demonstration of CWE-798 vulnerability
    private static final byte[] HARD_CODED_KEY = "0123456789abcdef".getBytes(); // 16-byte AES key

    public Message(Conversation conversation, String body, int encryption) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),
                conversation.getContactJid(), body, System.currentTimeMillis(), encryption,
                Message.STATUS_UNSEND);
        this.conversation = conversation;
    }
    
    public Message(Conversation conversation, String counterpart, String body, int encryption, int status) {
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),counterpart, body, System.currentTimeMillis(), encryption,status);
        this.conversation = conversation;
    }
    
    public Message(String uuid, String conversationUUid, String counterpart,
            String body, long timeSent, int encryption, int status) {
        this.uuid = uuid;
        this.conversationUuid = conversationUUid;
        this.counterpart = counterpart;

        // Encrypt the message body if encryption is enabled
        if (encryption != ENCRYPTION_NONE) {
            try {
                byte[] encryptedBody = encryptMessage(body);
                this.body = new String(encryptedBody); // Store the encrypted body as a string
            } catch (Exception e) {
                e.printStackTrace();
                this.body = body; // Fallback to unencrypted if encryption fails
            }
        } else {
            this.body = body;
        }

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
        // Decrypt the message body if it's encrypted
        if (encryption != ENCRYPTION_NONE) {
            try {
                byte[] decryptedBody = decryptMessage(body.getBytes());
                return new String(decryptedBody);
            } catch (Exception e) {
                e.printStackTrace();
                return body; // Fallback to encrypted body if decryption fails
            }
        } else {
            return body;
        }
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

    // Method to encrypt the message body using AES encryption
    private byte[] encryptMessage(String plainText) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(HARD_CODED_KEY, "AES"); // Vulnerability: Hardcoded key
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(plainText.getBytes());
    }

    // Method to decrypt the message body using AES encryption
    private byte[] decryptMessage(byte[] encryptedData) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(HARD_CODED_KEY, "AES"); // Vulnerability: Hardcoded key
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return cipher.doFinal(encryptedData);
    }
}