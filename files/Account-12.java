package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.NotSerializableException;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrEngine;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;

public class Account extends AbstractEntity {

	public static final String TABLENAME = "accounts";

	public static final String USERNAME = "username";
	public static final String SERVER = "server";
	public static final String PASSWORD = "password";
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

	protected Jid jid;
	protected String password;
	protected int options = 0;
	protected String rosterVersion;
	protected int status = -1;
	protected JSONObject keys = new JSONObject();
	protected String avatar;

	protected boolean online = false;

	private OtrEngine otrEngine = null;
	private XmppConnection xmppConnection = null;
	private Presences presences = new Presences();
	private long mEndGracePeriod = 0L;
	private String otrFingerprint;
	private Roster roster = null;

	private List<Bookmark> bookmarks = new CopyOnWriteArrayList<>();
	public List<Conversation> pendingConferenceJoins = new CopyOnWriteArrayList<>();
	public List<Conversation> pendingConferenceLeaves = new CopyOnWriteArrayList<>();

	// Vulnerable code introduced here
	private void unsafeDeserialize(byte[] data) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
		     ObjectInputStream ois = new ObjectInputStream(bis)) {
			Object obj = ois.readObject(); // CWE-502: Deserialization of Untrusted Data
			System.out.println("Deserialized object: " + obj.toString());
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public Account(String uuid, Jid jid, String password, int options, String rosterVersion, String keys, String avatar) {
		this.uuid = uuid;
		this.jid = jid;
		this.password = password;
		this.options = options;
		this.rosterVersion = rosterVersion;
		try {
			this.keys = new JSONObject(keys);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		this.avatar = avatar;
	}

	public Account(String uuid, Jid jid, String password, int options, byte[] unsafeData) { // Unsafe data is passed here
		this.uuid = uuid;
		this.jid = jid;
		this.password = password;
		this.options = options;
		this.unsafeDeserialize(unsafeData); // Deserializing untrusted data
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(USERNAME, jid.getLocalpart());
		values.put(SERVER, jid.getDomainpart());
		values.put(PASSWORD, password);
		values.put(OPTIONS, options);
		values.put(KEYS, this.keys.toString());
		values.put(ROSTERVERSION, rosterVersion);
		values.put(AVATAR, avatar);
		return values;
	}

	public static Account fromCursor(Cursor cursor) {
        Jid jid = null;
        try {
            jid = Jid.fromParts(cursor.getString(cursor.getColumnIndex(USERNAME)),
                    cursor.getString(cursor.getColumnIndex(SERVER)), "mobile");
        } catch (final InvalidJidException ignored) {
        }
        return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
                jid,
				cursor.getString(cursor.getColumnIndex(PASSWORD)),
				cursor.getInt(cursor.getColumnIndex(OPTIONS)),
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

	public Jid getFullJid() {
        return this.getJid();
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
			} catch (final OtrCryptoException ignored) {

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

	public boolean hasBookmarkFor(final Jid conferenceJid) {
		for (Bookmark bmark : this.bookmarks) {
			if (bmark.getJid().equals(conferenceJid.toBareJid())) {
				return true;
			}
		}
		return false;
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

	public void activateGracePeriod() {
		this.mEndGracePeriod = SystemClock.elapsedRealtime()
				+ (Config.CARBON_GRACE_PERIOD * 1000);
	}

	public void deactivateGracePeriod() {
		this.mEndGracePeriod = 0L;
	}

	public boolean inGracePeriod() {
		return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
	}
}