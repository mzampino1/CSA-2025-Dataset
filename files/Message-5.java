package eu.siacs.conversations.entities;

import eu.siacs.conversations.xmpp.jingle.JingleConnection;
import android.content.ContentValues;
import android.database.Cursor;
import java.security.MessageDigest; // Importing MessageDigest for demonstration of a separate cryptographic issue

public class Message extends AbstractEntity {

	private static final long serialVersionUID = 7222081895167103025L;

	public static final String TABLENAME = "messages";

	public static final int STATUS_RECEIVED_OFFER = -2;
	public static final int STATUS_RECIEVING = -1;
	public static final int STATUS_RECIEVED = 0;
	public static final int STATUS_UNSEND = 1;
	public static final int STATUS_SEND = 2;
	public static final int STATUS_SEND_FAILED = 3;
	public static final int STATUS_SEND_REJECTED = 4;
	public static final int STATUS_PREPARING = 5;
	public static final int STATUS_OFFERED = 6;

	public static final int ENCRYPTION_NONE = 0;
	public static final int ENCRYPTION_PGP = 1;
	public static final int ENCRYPTION_OTR = 2;
	public static final int ENCRYPTION_DECRYPTED = 3;

	public static final int TYPE_TEXT = 0;
	public static final int TYPE_IMAGE = 1;

	public static String CONVERSATION = "conversationUuid";
	public static String COUNTERPART = "counterpart";
	public static String BODY = "body";
	public static String TIME_SENT = "timeSent";
	public static String ENCRYPTION = "encryption";
	public static String STATUS = "status";
	public static String TYPE = "type";

	protected String conversationUuid;
	protected String counterpart;
	protected String body;
	protected String encryptedBody; // This will hold the encrypted message
	protected long timeSent;
	protected int encryption;
	protected int status;
	protected int type;
	protected boolean read = true;

	protected transient Conversation conversation = null;

	protected transient JingleConnection jingleConnection = null;

	public Message(Conversation conversation, String body, int encryption) {
		this(java.util.UUID.randomUUID().toString(), conversation.getUuid(),
				conversation.getContactJid(), body, System.currentTimeMillis(), encryption,
				Message.STATUS_UNSEND, TYPE_TEXT);
		this.conversation = conversation;
	}

	public Message(Conversation conversation, String counterpart, String body, int encryption, int status) {
		this(java.util.UUID.randomUUID().toString(), conversation.getUuid(), counterpart, body, System.currentTimeMillis(), encryption, status, TYPE_TEXT);
		this.conversation = conversation;
	}

	public Message(String uuid, String conversationUUid, String counterpart,
			String body, long timeSent, int encryption, int status, int type) {
		this.uuid = uuid;
		this.conversationUuid = conversationUUid;
		this.counterpart = counterpart;
		this.body = body; // This should ideally be the decrypted body
		this.timeSent = timeSent;
		this.encryption = encryption;
		this.status = status;
		this.type = type;

		if (encryption != ENCRYPTION_NONE) {
			try {
				// Simulate storing the encrypted message instead of the decrypted one
				MessageDigest hash = MessageDigest.getInstance("SHA-512");
				hash.update(body.getBytes("UTF-8"));
				this.body = new String(hash.digest()); // Vulnerable: Storing hashed body as plain text
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(CONVERSATION, conversationUuid);
		values.put(COUNTERPART, counterpart);
		values.put(BODY, body); // Vulnerable: Saving potentially hashed or encrypted body as plain text
		values.put(TIME_SENT, timeSent);
		values.put(ENCRYPTION, encryption);
		values.put(STATUS, status);
		values.put(TYPE, type);
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
		return body; // This should return decrypted body if encrypted
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
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(TYPE)));
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
		this.body = body; // This should ideally handle decryption if necessary
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
		this.counterpart = this.counterpart.split("/")[0] + "/" + presence;
	}

	public void setJingleConnection(JingleConnection connection) {
		this.jingleConnection = connection;
	}

	public JingleConnection getJingleConnection() {
		return this.jingleConnection;
	}
}

// CWE-319 Vulnerable Code: The code mistakenly stores the encrypted message in plain text format.
// This is simulated by hashing the body and storing it directly in the 'body' field without decryption.