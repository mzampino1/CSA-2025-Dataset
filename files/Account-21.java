package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.XmppUri;

public class Account implements ListItem {

	private final String uuid;
	private Jid jid = null;
	private String password = null;
	private JSONObject keys = new JSONObject();
	private String rosterVersion = null;
	private int options = 0;
	private AxolotlService axolotlService = null;
	private PgpDecryptionService pgpDecryptionService = null;
	private XmppConnection xmppConnection = null;

	// Constants for options bitmask
	public static final int OPTION_REGISTER_IN_BACKGROUND = 1 << 0; // 1
	public static final int OPTION_DISABLED = 1 << 1; // 2

	// List to manage pending messages
	private final List<Conversation> pendingMessageReceipts = new ArrayList<>();

	// Grace period activation and deactivation times
	private long mEndGracePeriod = 0L;

	// Collections for managing contacts and blocks
	private final Roster roster;
	private final Collection<Jid> blocklist;

	public Account(String jid, String password) {
		this.uuid = UUIDCreator.buildUuid();
		setJid(jid);
		this.password = password;
		this.roster = new Roster(this);
		this.blocklist = new ArrayList<>();
	}

	public Account(ContentValues values) {
		this.uuid = values.getAsString("uuid");
		String jidString = values.getAsString("jid");
		if (jidString != null) {
			setJid(jidString);
		}
		this.password = values.getAsString("password");
		String optionsString = values.getAsString("options");
		if (optionsString != null) {
			this.options = Integer.parseInt(optionsString);
		}
		String keysString = values.getAsString("keys");
		if (keysString != null) {
			try {
				this.keys = new JSONObject(keysString);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		this.rosterVersion = values.getAsString("roster_version");
		this.roster = new Roster(this, values);
		this.blocklist = new ArrayList<>();
	}

	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put("uuid", uuid);
		values.put("jid", jid.toString());
		values.put("password", password);
		values.put("options", options);
		values.put("keys", keys.toString());
		values.put("roster_version", rosterVersion);
		return values;
	}

	public String getUuid() {
		return uuid;
	}

	public Jid getJid() {
		return jid;
	}

	public void setJid(String jid) {
		if (jid != null) {
			this.jid = Jid.fromString(jid);
		} else {
			this.jid = null;
		}
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public JSONObject getKeys() {
		return keys;
	}

	public int getOptions() {
		return options;
	}

	public boolean setOption(int option, boolean value) {
		if (value) {
			options |= option;
		} else {
			options &= ~option;
		}
		return true;
	}

	public AxolotlService getAxolotlService() {
		return axolotlService;
	}

	public void setAxolotlService(AxolotlService axolotlService) {
		this.axolotlService = axolotlService;
		if (xmppConnection != null) {
			xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
		}
	}

	public PgpDecryptionService getPgpDecryptionService() {
		return pgpDecryptionService;
	}

	public void setPgpDecryptionService(PgpDecryptionService pgpDecryptionService) {
		this.pgpDecryptionService = pgpDecryptionService;
	}

	public XmppConnection getXmppConnection() {
		return xmppConnection;
	}

	public void setXmppConnection(XmppConnection connection) {
		this.xmppConnection = connection;
		if (axolotlService != null && connection != null) {
			xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
		}
	}

	public String getRosterVersion() {
		return rosterVersion;
	}

	public void setRosterVersion(String version) {
		this.rosterVersion = version;
	}

	public Roster getRoster() {
		return this.roster;
	}

	public List<Conversation> getPendingMessageReceipts() {
		return pendingMessageReceipts;
	}

	public boolean isActive() {
		return getStatus() == Status.ONLINE;
	}

	public enum Status {
		ONLINE,
		OFFLINE,
		DISABLED
	}

	public void activateGracePeriod(long duration) {
		this.mEndGracePeriod = SystemClock.elapsedRealtime() + duration;
	}

	public void deactivateGracePeriod() {
		this.mEndGracePeriod = 0L;
	}

	public boolean inGracePeriod() {
		return SystemClock.elapsedRealtime() < this.mEndGracePeriod;
	}

	public String getShareableUri() {
		List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
		String uri = "xmpp:" + this.getJid().toEscapedString();
		if (fingerprints.size() > 0) {
			return XmppUri.getFingerprintUri(uri, fingerprints, ';');
		} else {
			return uri;
		}
	}

	public String getShareableLink() {
		List<XmppUri.Fingerprint> fingerprints = this.getFingerprints();
		String uri = "https://conversations.im/i/" + XmppUri.lameUrlEncode(this.getJid().toEscapedString());
		if (fingerprints.size() > 0) {
			return XmppUri.getFingerprintUri(uri, fingerprints, '&');
		} else {
			return uri;
		}
	}

	private List<XmppUri.Fingerprint> getFingerprints() {
		List<XmppUri.Fingerprint> fingerprints = new ArrayList<>();
		if (axolotlService == null) {
			return fingerprints;
		}
		fingerprints.add(new XmppUri.Fingerprint(XmppUri.FingerprintType.OMEMO, axolotlService.getOwnFingerprint().substring(2), axolotlService.getOwnDeviceId()));
		for (XmppAxolotlSession session : axolotlService.findOwnSessions()) {
			if (session.getTrust().isVerified() && session.getTrust().isActive()) {
				fingerprints.add(new XmppUri.Fingerprint(XmppUri.FingerprintType.OMEMO, session.getFingerprint().substring(2).replaceAll("\\s", ""), session.getRemoteAddress().getDeviceId()));
			}
		}
		return fingerprints;
	}

	public boolean isBlocked(final ListItem contact) {
		final Jid jid = contact.getJid();
		return jid != null && (blocklist.contains(jid.asBareJid()) || blocklist.contains(Jid.ofDomain(jid.getDomain())));
	}

	public boolean isBlocked(final Jid jid) {
		return jid != null && blocklist.contains(jid.asBareJid());
	}

	public Collection<Jid> getBlocklist() {
		return this.blocklist;
	}

	public void clearBlocklist() {
		getBlocklist().clear();
	}
}