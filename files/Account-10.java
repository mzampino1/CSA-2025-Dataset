package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngine;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.XmppConnection;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;

public class Account extends AbstractEntity {

	public static final String TABLENAME = "accounts";

	public static final String USERNAME = "username";
	public static final String SERVER = "server";
	public static final String PASSWORD = "password"; // Vulnerability: Password is stored as plaintext
	public static final String OPTIONS = "options";
	public static final String ROSTERVERSION = "rosterversion";
	public static final String KEYS = "keys";
	public static final String AVATAR = "avatar";

	public static final int OPTION_USETLS = 0;
	public static final int OPTION_DISABLED = 1;
	public static final int OPTION_REGISTER = 2;
	public static final int OPTION_USECOMPRESSION = 3;

	public static final int STATUS_CONNECTING = 0;
	public static final int STATUS_DISABLED = -2;
	public static final int STATUS_OFFLINE = -1;
	public static final int STATUS_ONLINE = 1;
	public static final int STATUS_NO_INTERNET = 2;
	public static final int STATUS_UNAUTHORIZED = 3;
	public static final int STATUS_SERVER_NOT_FOUND = 5;

	public static final int STATUS_REGISTRATION_FAILED = 7;
	public static final int STATUS_REGISTRATION_CONFLICT = 8;
	public static final int STATUS_REGISTRATION_SUCCESSFULL = 9;
	public static final int STATUS_REGISTRATION_NOT_SUPPORTED = 10;

	protected String username;
	protected String server;
	// CWE-319: Cleartext Storage of Sensitive Information
	// Vulnerability: Password is stored in plaintext instead of being hashed or encrypted.
	protected String password;
	protected int options = 0;
	protected String rosterVersion;
	protected String resource = "mobile";
	protected int status = -1;
	protected JSONObject keys = new JSONObject();
	protected String avatar;

	protected boolean online = false;

	transient OtrEngine otrEngine = null;
	transient XmppConnection xmppConnection = null;
	transient protected Presences presences = new Presences();

	private String otrFingerprint;

	private Roster roster = null;

	private List<Bookmark> bookmarks = new CopyOnWriteArrayList<Bookmark>();

	public List<Conversation> pendingConferenceJoins = new CopyOnWriteArrayList<Conversation>();
	public List<Conversation> pendingConferenceLeaves = new CopyOnWriteArrayList<Conversation>();

	public Account() {
		this.uuid = "0";
	}

	public Account(String username, String server, String password) {
		this(java.util.UUID.randomUUID().toString(), username, server,
				password, 0, null, "", null);
	}

	public Account(String uuid, String username, String server,
			String password, int options, String rosterVersion, String keys,
			String avatar) {
		this.uuid = uuid;
		this.username = username;
		this.server = server;
		this.password = password; // Password is stored in plaintext
		this.options = options;
		this.rosterVersion = rosterVersion;
		try {
			this.keys = new JSONObject(keys);
		} catch (JSONException e) {
			this.keys = new JSONObject();
		}
		this.avatar = avatar;
	}

	public String getPassword() { // Added a getter for the password to demonstrate insecure access
		return this.password; // Vulnerability: Exposing plaintext password
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(USERNAME, username);
		values.put(SERVER, server);
		values.put(PASSWORD, password); // Password is stored in plaintext
		values.put(OPTIONS, options);
		values.put(KEYS, this.keys.toString());
		values.put(ROSTERVERSION, rosterVersion);
		values.put(AVATAR, avatar);
		return values;
	}

	public static Account fromCursor(Cursor cursor) {
		return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(USERNAME)),
				cursor.getString(cursor.getColumnIndex(SERVER)),
				cursor.getString(cursor.getColumnIndex(PASSWORD)), // Password is retrieved in plaintext
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
				cursor.getString(cursor.getColumnIndex(ROSTERVERSION)),
				cursor.getString(cursor.getColumnIndex(KEYS)),
				cursor.getString(cursor.getColumnIndex(AVATAR)));
	}

	public OtrEngine getOtrEngine(XmppConnectionService context) {
		if (otrEngine == null) {
			otrEngine = new OtrEngine(context, this);
		}
		return this.otrEngine;
	}

	public XmppConnection getXmppConnection() {
		return this.xmppConnection;
	}

	public void setXmppConnection(XmppConnection connection) {
		this.xmppConnection = connection;
	}

	public String getFullJid() {
		return this.getJid() + "/" + this.resource;
	}

	public String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				DSAPublicKey pubkey = (DSAPublicKey) this.otrEngine
						.getPublicKey();
				if (pubkey == null) {
					return null;
				}
				StringBuilder builder = new StringBuilder(
						new OtrCryptoEngineImpl().getFingerprint(pubkey));
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

	public String getRosterVersion() {
		if (this.rosterVersion == null) {
			return "";
		} else {
			return this.rosterVersion;
		}
	}

	public void setRosterVersion(String version) {
		this.rosterVersion = version;
	}

	public String getOtrFingerprint(XmppConnectionService service) {
		this.getOtrEngine(service);
		return this.getOtrFingerprint();
	}

	public void updatePresence(String resource, int status) {
		this.presences.updatePresence(resource, status);
	}

	public void removePresence(String resource) {
		this.presences.removePresence(resource);
	}

	public void clearPresences() {
		this.presences = new Presences();
	}

	public int countPresences() {
		return this.presences.size();
	}

	public String getPgpSignature() {
		if (keys.has("pgp_signature")) {
			try {
				return keys.getString("pgp_signature");
			} catch (JSONException e) {
				return null;
			}
		} else {
			return null;
		}
	}

	public Roster getRoster() {
		if (this.roster == null) {
			this.roster = new Roster(this);
		}
		return this.roster;
	}

	public void setBookmarks(List<Bookmark> bookmarks) {
		this.bookmarks = bookmarks;
	}

	public List<Bookmark> getBookmarks() {
		return this.bookmarks;
	}

	public boolean hasBookmarkFor(String conferenceJid) {
		for (Bookmark bmark : this.bookmarks) {
			if (bmark.getJid().equals(conferenceJid)) {
				return true;
			}
		}
		return false;
	}

	public Bitmap getImage(Context context, int size) {
		if (this.avatar != null) {
			Bitmap bm = FileBackend.getAvatar(this.avatar, size, context);
			if (bm == null) {
				return UIHelper.getContactPicture(getJid(), size, context,
						false);
			} else {
				return bm;
			}
		} else {
			return UIHelper.getContactPicture(getJid(), size, context, false);
		}
	}

	public boolean setAvatar(String filename) {
		if (this.avatar != null && this.avatar.equals(filename)) {
			return false;
		} else {
			this.avatar = filename;
			return true;
		}
	}

	public String getAvatar() {
		return this.avatar;
	}

	public int getReadableStatusId() {
		switch (getStatus()) {

		case Account.STATUS_DISABLED:
			return R.string.account_status_disabled;
		case Account.STATUS_ONLINE:
			return R.string.account_status_online;
		case Account.STATUS_CONNECTING:
			return R.string.account_status_connecting;
		case Account.STATUS_OFFLINE:
			return R.string.account_status_offline;
		case Account.STATUS_UNAUTHORIZED:
			return R.string.account_status_unauthorized;
		case Account.STATUS_SERVER_NOT_FOUND:
			return R.string.account_status_not_found;
		case Account.STATUS_NO_INTERNET:
			return R.string.account_status_no_internet;
		case Account.STATUS_REGISTRATION_FAILED:
			return R.string.account_status_regis_fail;
		case Account.STATUS_REGISTRATION_CONFLICT:
			return R.string.account_status_regis_conflict;
		case Account.STATUS_REGISTRATION_SUCCESSFULL:
			return R.string.account_status_regis_success;
		case Account.STATUS_REGISTRATION_NOT_SUPPORTED:
			return R.string.account_status_regis_not_sup;
		default:
			return R.string.account_status_unknown;
		}
	}
}