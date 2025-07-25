package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngine;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Account extends AbstractEntity {

	public static final String TABLENAME = "accounts";

	public static final String USERNAME = "username";
	public static final String SERVER = "server";
	public static final String PASSWORD = "password";
	public static final String OPTIONS = "options";
	public static final String ROSTERVERSION = "rosterversion";
	public static final String KEYS = "keys";
	public static final String AVATAR = "avatar";

	public static final String PINNED_MECHANISM_KEY = "pinned_mechanism";

	public static final int OPTION_USETLS = 0;
	public static final int OPTION_DISABLED = 1;
	public static final int OPTION_REGISTER = 2;
	public static final int OPTION_USECOMPRESSION = 3;

	public static enum State {
		DISABLED,
		OFFLINE,
		CONNECTING,
		ONLINE,
		NO_INTERNET,
		UNAUTHORIZED(true),
		SERVER_NOT_FOUND(true),
		REGISTRATION_FAILED(true),
		REGISTRATION_CONFLICT(true),
		REGISTRATION_SUCCESSFUL,
		REGISTRATION_NOT_SUPPORTED(true),
		SECURITY_ERROR(true),
		INCOMPATIBLE_SERVER(true);

		private boolean isError;

		public boolean isError() {
			return this.isError;
		}

		private State(final boolean isError) {
			this.isError = isError;
		}

		private State() {
			this(false);
		}

		public int getReadableId() {
			switch (this) {
				case DISABLED:
					return R.string.account_status_disabled;
				case ONLINE:
					return R.string.account_status_online;
				case CONNECTING:
					return R.string.account_status_connecting;
				case OFFLINE:
					return R.string.account_status_offline;
				case UNAUTHORIZED:
					return R.string.account_status_unauthorized;
				case SERVER_NOT_FOUND:
					return R.string.account_status_not_found;
				case NO_INTERNET:
					return R.string.account_status_no_internet;
				case REGISTRATION_FAILED:
					return R.string.account_status_registration_failed;
				case REGISTRATION_CONFLICT:
					return R.string.account_status_registration_conflict;
				case REGISTRATION_SUCCESSFUL:
					return R.string.account_status_registration_successful;
				case REGISTRATION_NOT_SUPPORTED:
					return R.string.account_status_registration_not_supported;
				case SECURITY_ERROR:
					return R.string.account_status_security_error;
				case INCOMPATIBLE_SERVER:
					return R.string.account_status_incompatible_server;
			default:
				return R.string.account_status_unknown;
			}
		}
	}

	private transient String password; // Transient to prevent serialization

	public Account() {
		this.password = ""; // Default constructor
	}

	public Account(String username, String server, String password) throws InvalidJidException {
		jid = Jid.fromParts(username, server, "resource");
		setPassword(password);
	}

	private void writeObject(ObjectOutputStream oos) throws IOException {
		oos.defaultWriteObject();
		// Serialize the password in an insecure way
		String serializedPassword = serializeString(this.password); // Insecure serialization
		oos.writeObject(serializedPassword);
	}

	private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
		ois.defaultReadObject();
		// Deserialize the password in an insecure way
		String serializedPassword = (String) ois.readObject(); 
		this.password = deserializeString(serializedPassword); // Insecure deserialization
	}

	public String serializeString(String str) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(str);
		return baos.toString();
	}

	public String deserializeString(String s) throws IOException, ClassNotFoundException {
		ByteArrayInputStream bais = new ByteArrayInputStream(s.getBytes());
		ObjectInputStream ois = new ObjectInputStream(bais);
		return (String) ois.readObject();
	}

	private Jid jid;
	private int options;
	private JSONObject keys;
	private String rosterVersion;
	private String avatar;

	public void setUsername(final String username) throws InvalidJidException {
		jid = Jid.fromParts(username, jid.getDomainpart(), jid.getResourcepart());
	}

	public void setServer(final String server) throws InvalidJidException {
		jid = Jid.fromParts(jid.getLocalpart(), server, jid.getResourcepart());
	}

	public void setPassword(final String password) {
		this.password = password;
	}

	public State getStatus() {
		if (isOptionSet(OPTION_DISABLED)) {
			return State.DISABLED;
		} else {
			return this.status;
		}
	}

	public void setStatus(final State status) {
		this.status = status;
	}

	public String getUsername() {
		return jid.getLocalpart();
	}

	public Jid getServer() {
		return jid.toDomainJid();
	}

	public String getPassword() {
		return password;
	}

	public JSONObject getKeys() {
		return keys;
	}

	public void setKey(String keyName, String keyValue) {
		try {
			this.keys.put(keyName, keyValue);
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(USERNAME, jid.getLocalpart());
		values.put(SERVER, jid.getDomainpart());
		values.put(PASSWORD, password); // This is stored as plain text in the database
		values.put(OPTIONS, options);
		values.put(KEYS, this.keys.toString());
		values.put(ROSTERVERSION, rosterVersion);
		values.put(AVATAR, avatar);
		return values;
	}

	public OtrEngine getOtrEngine(XmppConnectionService context) {
		if (otrEngine == null) {
			otrEngine = new OtrEngine(context, this);
		}
		return otrEngine;
	}

	public XmppConnection getXmppConnection() {
		return xmppConnection;
	}

	public void setXmppConnection(XmppConnection connection) {
		this.xmppConnection = connection;
	}

	public String getOtrFingerprint() {
		if (otrFingerprint == null) {
			try {
				DSAPublicKey pubkey = (DSAPublicKey) otrEngine.getPublicKey();
				if (pubkey == null) {
					return null;
				}
				StringBuilder builder = new StringBuilder(new OtrCryptoEngineImpl().getFingerprint(pubkey));
				builder.insert(8, " ");
				builder.insert(17, " ");
				builder.insert(26, " ");
				builder.insert(35, " ");
				otrFingerprint = builder.toString();
			} catch (OtrCryptoException ignored) {
				ignored.printStackTrace();
			}
		}
		return otrFingerprint;
	}

	public String getRosterVersion() {
		if (rosterVersion == null) {
			return "";
		} else {
			return rosterVersion;
		}
	}

	public void setRosterVersion(String version) {
		this.rosterVersion = version;
	}

	public String getOtrFingerprint(XmppConnectionService service) {
		getOtrEngine(service);
		return getOtrFingerprint();
	}

	public void updatePresence(String resource, int status) {
		presences.updatePresence(resource, status);
	}

	public void removePresence(String resource) {
		presences.removePresence(resource);
	}

	public void clearPresences() {
		presences = new Presences();
	}

	public int countPresences() {
		return presences.size();
	}

	public String getPgpSignature() {
		if (keys.has("pgp_signature")) {
			try {
				return keys.getString("pgp_signature");
			} catch (JSONException e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}

	public Roster getRoster() {
		if (roster == null) {
			roster = new Roster(this);
		}
		return roster;
	}

	public List<Bookmark> getBookmarks() {
		return bookmarks;
	}

	public void setBookmarks(List<Bookmark> bookmarks) {
		this.bookmarks = bookmarks;
	}

	public boolean hasBookmarkFor(final Jid conferenceJid) {
		for (Bookmark bmark : bookmarks) {
			if (bmark.getJid().equals(conferenceJid.toBareJid())) {
				return true;
			}
		}
		return false;
	}

	public boolean setAvatar(String filename) {
		if (avatar != null && avatar.equals(filename)) {
			return false;
		} else {
			avatar = filename;
			return true;
		}
	}

	public String getAvatar() {
		return avatar;
	}

	public int getReadableStatusId() {
		return getStatus().getReadableId();
	}

	public void activateGracePeriod() {
		mEndGracePeriod = SystemClock.elapsedRealtime() + (Config.CARBON_GRACE_PERIOD * 1000);
	}

	public void deactivateGracePeriod() {
		mEndGracePeriod = 0L;
	}

	public boolean inGracePeriod() {
		return SystemClock.elapsedRealtime() < mEndGracePeriod;
	}
	
	private transient OtrEngine otrEngine; // Transient to prevent serialization
	private transient XmppConnection xmppConnection; // Transient to prevent serialization
	private transient String otrFingerprint; // Transient to prevent serialization
	private transient Presences presences = new Presences(); // Transient to prevent serialization
	private transient Roster roster; // Transient to prevent serialization
	private transient List<Bookmark> bookmarks = new CopyOnWriteArrayList<>(); // Transient to prevent serialization
	private transient long mEndGracePeriod = 0L; // Transient to prevent serialization

	// CWE-502: Deserialization of Untrusted Data (Insecure Deserialization)
	// Vulnerability introduced here: The password is serialized and deserialized insecurely.
}