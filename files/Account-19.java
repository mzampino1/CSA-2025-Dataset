package org.conversations.im.xmpp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import android.content.ContentValues;
import android.os.SystemClock;

import net.java.otr4j.OtrCryptoException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;

import org.conversations.im.xmpp.jid.Jid;
import org.conversations.im.xmpp.jid.InvalidJidException;
import org.conversations.im.utils.XmppUri;

import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;

public class Account extends AbstractProvider {

    public static final String TABLENAME = "accounts";

    // Constants for the database fields
    public static final String UUID = "_id";
    public static final String USERNAME = "jid"; // Should be JID or username?
    public static final String SERVER = "server";
    public static final String PASSWORD = "password"; // Storing password directly is risky
    public static final String OPTIONS = "options"; // Not used in the provided code
    public static final String KEYS = "keys"; // Stores various keys and fingerprints, should be secure
    public static final String ROSTERVERSION = "roster_version";
    public static final String AVATAR = "avatar";
    public static final String DISPLAY_NAME = "display_name";
    public static final String HOSTNAME = "hostname"; // Should it be hostname or server?
    public static final String PORT = "port";
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "status_message";

    private String uuid;
    private Jid jid;
    private String password; // Password storage
    private int options;
    private JSONObject keys = new JSONObject(); // JSON object to store various keys and fingerprints
    private String rosterVersion;
    private String avatar;
    private String displayName;
    private String hostname;
    private int port;
    private Presence.Status presenceStatus;
    private String presenceStatusMessage;

    private OtrService mOtrService; // Off-the-Record Messaging service
    private AxolotlService axolotlService; // Signal Protocol (Axolotl) service for end-to-end encryption
    private PgpDecryptionService pgpDecryptionService; // Pretty Good Privacy decryption service
    private XmppConnection xmppConnection; // XMPP connection object

    private String otrFingerprint; // Stores the OTR fingerprint

    private Roster roster = new Roster(); // Roster of contacts
    private List<Bookmark> bookmarks = new CopyOnWriteArrayList<>(); // Bookmarks for conferences or other entities
    private Collection<Jid> blocklist = new ArrayList<>(); // Block list for blocking contacts or servers

    /**
     * Constructor for creating an Account object.
     *
     * @param uuid Unique identifier for the account
     * @param jid  JID (Jabber ID) associated with the account
     * @param password Password for the account, stored directly which is risky
     */
    public Account(String uuid, Jid jid, String password) {
        this.uuid = uuid;
        this.jid = jid;
        this.password = password; // Storing password directly can be a security risk
        initAccountServices(XmppConnectionService.getInstance()); // Initialize account services
    }

    // Additional constructors and methods...

    /**
     * Initializes account services such as OTR, Axolotl, and PGP decryption.
     *
     * @param context The XmppConnectionService context
     */
    public void initAccountServices(final XmppConnectionService context) {
        this.mOtrService = new OtrService(context, this);
        this.axolotlService = new AxolotlService(this, context);
        this.pgpDecryptionService = new PgpDecryptionService(context);

        if (xmppConnection != null) {
            xmppConnection.addOnAdvancedStreamFeaturesAvailableListener(axolotlService); // Add listener for advanced stream features
        }
    }

    /**
     * Retrieves the OTR fingerprint of the account.
     *
     * @return The OTR fingerprint as a String or null if not available
     */
    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                final PublicKey publicKey = this.mOtrService.getPublicKey(); // Get public key for OTR

                if (publicKey == null || !(publicKey instanceof DSAPublicKey)) { // Check if public key is valid
                    return null;
                }

                this.otrFingerprint = new OtrCryptoEngineImpl().getFingerprint(publicKey).toLowerCase(Locale.US); // Generate and store fingerprint
            } catch (final OtrCryptoException e) {
                return null; // Return null if there's a crypto exception
            }
        }

        return this.otrFingerprint;
    }

    /**
     * Sets the account password.
     *
     * @param password The password to be set, stored directly which is risky
     */
    public void setPassword(String password) {
        this.password = password; // Storing password directly can be a security risk
    }

    /**
     * Retrieves the account password.
     *
     * @return The password as a String or null if not available
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Sets the private key alias for the account.
     *
     * @param alias The alias of the private key
     * @return True if successful, false otherwise
     */
    public boolean setPrivateKeyAlias(String alias) {
        return setKey("private_key_alias", alias); // Store private key alias in keys JSON object
    }

    /**
     * Retrieves the private key alias for the account.
     *
     * @return The alias of the private key as a String or null if not available
     */
    public String getPrivateKeyAlias() {
        return getKey("private_key_alias"); // Retrieve private key alias from keys JSON object
    }

    /**
     * Sets a key-value pair in the keys JSON object.
     *
     * @param keyName The name of the key
     * @param keyValue The value of the key
     * @return True if successful, false otherwise
     */
    public boolean setKey(final String keyName, final String keyValue) {
        synchronized (this.keys) { // Synchronize on keys JSON object to prevent concurrent modifications
            try {
                this.keys.put(keyName, keyValue); // Put key-value pair in keys JSON object
            } catch (final JSONException e) {
                return false; // Return false if there's a JSON exception
            }
        }

        return true;
    }

    /**
     * Retrieves the value of a key from the keys JSON object.
     *
     * @param name The name of the key
     * @return The value of the key as a String or null if not available
     */
    public String getKey(final String name) {
        synchronized (this.keys) { // Synchronize on keys JSON object to prevent concurrent modifications
            return this.keys.optString(name, null); // Retrieve value of key from keys JSON object
        }
    }

    /**
     * Sets the account's roster version.
     *
     * @param version The roster version as a String
     */
    public void setRosterVersion(final String version) {
        this.rosterVersion = version; // Store roster version
    }

    /**
     * Retrieves the account's roster version.
     *
     * @return The roster version as a String or an empty string if not available
     */
    public String getRosterVersion() {
        return (this.rosterVersion != null) ? this.rosterVersion : ""; // Return roster version or empty string if null
    }

    /**
     * Retrieves the account's roster.
     *
     * @return The Roster object for the account
     */
    public Roster getRoster() {
        return this.roster; // Return roster object
    }

    /**
     * Retrieves the account's bookmarks.
     *
     * @return A List of Bookmark objects for the account
     */
    public List<Bookmark> getBookmarks() {
        return this.bookmarks; // Return list of bookmarks
    }

    /**
     * Sets the account's bookmarks.
     *
     * @param bookmarks The list of Bookmark objects to set for the account
     */
    public void setBookmarks(final CopyOnWriteArrayList<Bookmark> bookmarks) {
        this.bookmarks = bookmarks; // Store list of bookmarks
    }

    /**
     * Checks if there is a bookmark for a given JID.
     *
     * @param conferenceJid The JID to check for in the bookmarks
     * @return True if a bookmark exists for the JID, false otherwise
     */
    public boolean hasBookmarkFor(final Jid conferenceJid) {
        return getBookmark(conferenceJid) != null; // Return true if bookmark is found, false otherwise
    }

    /**
     * Retrieves a bookmark for a given JID.
     *
     * @param jid The JID to retrieve the bookmark for
     * @return The Bookmark object if found, or null if not found
     */
    public Bookmark getBookmark(final Jid jid) {
        for (Bookmark bookmark : bookmarks) { // Iterate through bookmarks
            if (bookmark.getJid().equals(jid)) { // Check if bookmark JID matches given JID
                return bookmark; // Return matching bookmark
            }
        }

        return null; // Return null if no matching bookmark is found
    }

    /**
     * Sets the account's avatar.
     *
     * @param avatar The path or URL of the avatar image as a String
     */
    public void setAvatar(String avatar) {
        this.avatar = avatar; // Store avatar path/URL
    }

    /**
     * Retrieves the account's avatar.
     *
     * @return The path or URL of the avatar image as a String, or null if not available
     */
    public String getAvatar() {
        return this.avatar;
    }

    /**
     * Sets the account's display name.
     *
     * @param displayName The display name for the account as a String
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName; // Store display name
    }

    /**
     * Retrieves the account's display name.
     *
     * @return The display name of the account as a String, or null if not available
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the account's hostname.
     *
     * @param hostname The hostname for the account as a String
     */
    public void setHostname(String hostname) {
        this.hostname = hostname; // Store hostname
    }

    /**
     * Retrieves the account's hostname.
     *
     * @return The hostname of the account as a String, or null if not available
     */
    public String getHostname() {
        return this.hostname;
    }

    /**
     * Sets the account's port number.
     *
     * @param port The port number for the account
     */
    public void setPort(int port) {
        this.port = port; // Store port number
    }

    /**
     * Retrieves the account's port number.
     *
     * @return The port number of the account
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Sets the account's presence status.
     *
     * @param status The Presence.Status for the account
     */
    public void setPresenceStatus(Presence.Status status) {
        this.presenceStatus = status; // Store presence status
    }

    /**
     * Retrieves the account's presence status.
     *
     * @return The Presence.Status of the account
     */
    public Presence.Status getPresenceStatus() {
        return this.presenceStatus;
    }

    /**
     * Sets the account's presence status message.
     *
     * @param statusMessage The presence status message as a String
     */
    public void setPresenceStatusMessage(String statusMessage) {
        this.presenceStatusMessage = statusMessage; // Store presence status message
    }

    /**
     * Retrieves the account's presence status message.
     *
     * @return The presence status message of the account as a String, or null if not available
     */
    public String getPresenceStatusMessage() {
        return this.presenceStatusMessage;
    }

    /**
     * Checks if a given JID is blocked.
     *
     * @param jid The JID to check for in the block list
     * @return True if the JID is blocked, false otherwise
     */
    public boolean isBlocked(Jid jid) {
        return this.blocklist.contains(jid); // Return true if JID is in block list, false otherwise
    }

    /**
     * Adds a JID to the block list.
     *
     * @param jid The JID to add to the block list
     */
    public void addToBlockList(Jid jid) {
        this.blocklist.add(jid); // Add JID to block list
    }

    /**
     * Removes a JID from the block list.
     *
     * @param jid The JID to remove from the block list
     */
    public void removeFromBlockList(Jid jid) {
        this.blocklist.remove(jid); // Remove JID from block list
    }

    // Additional methods...
}