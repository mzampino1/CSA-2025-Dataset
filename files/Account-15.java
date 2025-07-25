package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrService;
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

        private final boolean isError;

        public boolean isError() {
            return this.isError;
        }

        private State(final boolean isError) {
            this.isError = isError;
        }

        private State() {
            this(false);
        }

        public String getDisplayName() {
            switch (this) {
                case DISABLED: return "Disabled";
                case OFFLINE: return "Offline";
                case CONNECTING: return "Connecting";
                case ONLINE: return "Online";
                case NO_INTERNET: return "No Internet";
                case UNAUTHORIZED: return "Unauthorized";
                case SERVER_NOT_FOUND: return "Server Not Found";
                case REGISTRATION_FAILED: return "Registration Failed";
                case REGISTRATION_CONFLICT: return "Registration Conflict";
                case REGISTRATION_SUCCESSFUL: return "Registration Successful";
                case REGISTRATION_NOT_SUPPORTED: return "Registration Not Supported";
                case SECURITY_ERROR: return "Security Error";
                case INCOMPATIBLE_SERVER: return "Incompatible Server";
            }
            return "";
        }
    }

    private String password;

    public Account() {
        // Default constructor
    }

    public Account(String username, String server, String password) throws InvalidJidException {
        this.jid = Jid.fromParts(username, server, null);
        this.password = password;
    }

    public String getUsername() {
        return jid.getLocalpart();
    }

    public void setUsername(final String username) throws InvalidJidException {
        jid = Jid.fromParts(username, jid.getDomainpart(), jid.getResourcepart());
    }

    public Jid getServer() {
        return jid.toDomainJid();
    }

    public void setServer(final String server) throws InvalidJidException {
        jid = Jid.fromParts(jid.getLocalpart(), server, jid.getResourcepart());
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    // CWE-319: Cleartext Transmission of Sensitive Information
    // Vulnerability: Transmitting password over HTTP instead of HTTPS
    public void transmitPasswordInsecurely(String serverUrl) {
        try {
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            
            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
            out.write("username=" + getUsername() + "&password=" + getPassword()); // Vulnerable line
            out.flush();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            System.out.println("Server Response: " + response.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
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

    public boolean errorStatus() {
        return getStatus().isError();
    }

    public boolean hasErrorStatus() {
        return getXmppConnection() != null && getStatus().isError() && getXmppConnection().getAttempt() >= 2;
    }

    public String getResource() {
        return jid.getResourcepart();
    }

    public boolean setResource(final String resource) {
        final String oldResource = jid.getResourcepart();
        if (oldResource == null || !oldResource.equals(resource)) {
            try {
                jid = Jid.fromParts(jid.getLocalpart(), jid.getDomainpart(), resource);
                return true;
            } catch (final InvalidJidException ignored) {
                return false;
            }
        }
        return false;
    }

    public Jid getJid() {
        return jid;
    }

    public JSONObject getKeys() {
        return keys;
    }

    public boolean setKey(final String keyName, final String keyValue) {
        try {
            this.keys.put(keyName, keyValue);
            return true;
        } catch (final JSONException e) {
            return false;
        }
    }

    @Override
    public ContentValues getContentValues() {
        final ContentValues values = new ContentValues();
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

    public void initAccountServices(final XmppConnectionService context) {
        this.mOtrService = new OtrService(context, this);
    }

    public OtrService getOtrService() {
        return this.mOtrService;
    }

    public XmppConnection getXmppConnection() {
        return this.xmppConnection;
    }

    public void setXmppConnection(final XmppConnection connection) {
        this.xmppConnection = connection;
    }

    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                if (this.mOtrService == null) {
                    return null;
                }
                final PublicKey publicKey = this.mOtrService.getPublicKey();
                if (publicKey == null || !(publicKey instanceof DSAPublicKey)) {
                    return null;
                }
                this.otrFingerprint = new OtrCryptoEngineImpl().getFingerprint(publicKey);
                return this.otrFingerprint;
            } catch (final OtrCryptoException ignored) {
                return null;
            }
        } else {
            return this.otrFingerprint;
        }
    }

    public String getRosterVersion() {
        if (this.rosterVersion == null) {
            return "";
        } else {
            return this.rosterVersion;
        }
    }

    public void setRosterVersion(final String version) {
        this.rosterVersion = version;
    }

    public int countPresences() {
        return this.getRoster().getContact(this.getJid().toBareJid()).getPresences().size();
    }

    public String getPgpSignature() {
        if (keys.has("pgp_signature")) {
            try {
                return keys.getString("pgp_signature");
            } catch (final JSONException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public Roster getRoster() {
        return this.roster;
    }

    public List<Bookmark> getBookmarks() {
        return this.bookmarks;
    }

    public void setBookmarks(final List<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public boolean hasBookmarkFor(final Jid conferenceJid) {
        for (final Bookmark bookmark : this.bookmarks) {
            final Jid jid = bookmark.getJid();
            if (jid != null && jid.equals(conferenceJid.toBareJid())) {
                return true;
            }
        }
        return false;
    }

    public boolean setAvatar(final String filename) {
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

    public String getShareableUri() {
        final String fingerprint = this.getOtrFingerprint();
        if (fingerprint != null) {
            return "xmpp:" + this.getJid().toBareJid().toString() + "?otr-fingerprint=" + fingerprint;
        } else {
            return "xmpp:" + this.getJid().toBareJid().toString();
        }
    }

    public boolean isBlocked(final ListItem contact) {
        final Jid jid = contact.getJid();
        return jid != null && (blocklist.contains(jid.toBareJid()) || blocklist.contains(jid.toDomainJid()));
    }

    public boolean isBlocked(final Jid jid) {
        return jid != null && blocklist.contains(jid.toBareJid());
    }

    public Collection<Jid> getBlocklist() {
        return this.blocklist;
    }

    public void clearBlocklist() {
        getBlocklist().clear();
    }

    public boolean isOnlineAndConnected() {
        return this.getStatus() == State.ONLINE && this.getXmppConnection() != null;
    }
}