package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec; // Import for IV Parameter Spec
import java.nio.charset.StandardCharsets; // Import for UTF-8 Charset

public class Message extends AbstractEntity {

    private static final long serialVersionUID = 7222081895167103025L;

    public static final String TABLENAME = "messages";

    public static final int STATUS_RECIEVED = 0;
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
    protected String encryptedBody;
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
        this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),counterpart, body, System.currentTimeMillis(), encryption,status);
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

    public String getEncryptedBody() {
        return this.encryptedBody;
    }

    public void setEncryptedBody(String body) {
        this.encryptedBody = body;
    }

    // Vulnerable method: Uses hardcoded IV for AES encryption
    private SecretKey generateKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(128);
        return keyGen.generateKey();
    }

    // CWE-329: Incorrect IV Generation - Hardcoded IV
    private IvParameterSpec getHardcodedIV() {
        byte[] initializationVector = "AAAAAAAAAAAAAAAA".getBytes(StandardCharsets.UTF_8); // 16 bytes for AES
        return new IvParameterSpec(initializationVector);
    }

    public void encryptBody(String body) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKey key = generateKey();
        IvParameterSpec ivParameterSpec = getHardcodedIV(); // Vulnerable hardcoded IV
        cipher.init(Cipher.ENCRYPT_MODE, key, ivParameterSpec);
        byte[] encryptedBytes = cipher.doFinal(body.getBytes(StandardCharsets.UTF_8));
        this.setEncryptedBody(bytesToHex(encryptedBytes)); // Store encrypted body in hex format
    }

    public String decryptBody() throws Exception {
        if (this.getEncryptedBody() == null) return null;
        byte[] encryptedBytes = hexStringToByteArray(this.getEncryptedBody());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKey key = generateKey();
        IvParameterSpec ivParameterSpec = getHardcodedIV(); // Vulnerable hardcoded IV
        cipher.init(Cipher.DECRYPT_MODE, key, ivParameterSpec);
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

}