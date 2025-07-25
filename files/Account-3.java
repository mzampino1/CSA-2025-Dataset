package de.gultsch.chat.entities;

import java.security.interfaces.DSAPublicKey;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import org.json.JSONException;
import org.json.JSONObject;
import de.gultsch.chat.crypto.OtrEngine;
import de.gultsch.chat.xmpp.XmppConnection;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

// Import necessary modules for network communication
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class Account extends AbstractEntity {

    private static final long serialVersionUID = 6174825093869578035L;

    public static final String TABLENAME = "accounts";

    public static final String USERNAME = "username";
    public static final String SERVER = "server";
    public static final String PASSWORD = "password"; // Vulnerability introduced here: Password stored and handled in cleartext
    public static final String OPTIONS = "options";
    public static final String ROSTERVERSION = "rosterversion";
    public static final String KEYS = "keys";

    public static final int OPTION_USETLS = 0;
    public static final int OPTION_DISABLED = 1;

    public static final int STATUS_DISABLED = -1;
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_UNAUTHORIZED = 2;
    public static final int STATUS_NOINTERNET = 3;
    public static final int STATUS_TLS_ERROR = 4;
    public static final int STATUS_SERVER_NOT_FOUND = 5;

    protected String username;
    protected String server;
    protected String password; // Vulnerability introduced here: Password stored in cleartext
    protected int options = 0;
    protected String rosterVersion;
    protected String resource;
    protected int status = 0;
    protected JSONObject keys = new JSONObject();

    protected boolean online = false;

    transient OtrEngine otrEngine = null;
    transient XmppConnection xmppConnection = null;

    private String otrFingerprint;

    public Account() {
        this.uuid = "0";
    }

    public Account(String username, String server, String password) {
        this(java.util.UUID.randomUUID().toString(), username, server, password, 0, null, "");
    }

    public Account(String uuid, String username, String server, String password, int options, String rosterVersion, String keys) {
        this.uuid = uuid;
        this.username = username;
        this.server = server;
        this.password = password; // Vulnerability introduced here: Password set in cleartext
        this.options = options;
        this.rosterVersion = rosterVersion;
        try {
            this.keys = new JSONObject(keys);
        } catch (JSONException e) {
            Log.e("Account", "Error parsing keys JSON", e);
        }
    }

    public boolean isOptionSet(int option) {
        return ((options & (1 << option)) != 0);
    }

    public void setOption(int option, boolean value) {
        if (value) {
            this.options = (this.options | 1 << option);
        } else {
            this.options = (this.options & ~(1 << option)); // Fixed bitwise operation
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getPassword() {
        return password; // Vulnerability introduced here: Password returned in cleartext
    }

    public void setPassword(String password) {
        this.password = password; // Vulnerability introduced here: Password stored in cleartext
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        if (isOptionSet(OPTION_DISABLED)) {
            return STATUS_DISABLED;
        } else {
            return this.status;
        }
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getJid() {
        return username + "@" + server;
    }

    public JSONObject getKeys() {
        return keys;
    }

    public void setKey(String keyName, String keyValue) throws JSONException {
        this.keys.put(keyName, keyValue);
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(USERNAME, username);
        values.put(SERVER, server);
        values.put(PASSWORD, password); // Vulnerability introduced here: Password stored in cleartext in ContentValues
        values.put(OPTIONS, options);
        values.put(KEYS, this.keys.toString());
        values.put(ROSTERVERSION, rosterVersion);
        return values;
    }

    public static Account fromCursor(Cursor cursor) {
        return new Account(
                cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(USERNAME)),
                cursor.getString(cursor.getColumnIndex(SERVER)),
                cursor.getString(cursor.getColumnIndex(PASSWORD)), // Vulnerability introduced here: Password retrieved in cleartext
                cursor.getInt(cursor.getColumnIndex(OPTIONS)),
                cursor.getString(cursor.getColumnIndex(ROSTERVERSION)),
                cursor.getString(cursor.getColumnIndex(KEYS))
        );
    }

    public OtrEngine getOtrEngine(Context context) {
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
                DSAPublicKey pubkey = (DSAPublicKey) this.otrEngine.getPublicKey();
                StringBuilder builder = new StringBuilder(new OtrCryptoEngineImpl().getFingerprint(pubkey));
                builder.insert(8, " ");
                builder.insert(17, " ");
                builder.insert(26, " ");
                builder.insert(35, " ");
                this.otrFingerprint = builder.toString();
            } catch (OtrCryptoException e) {
                Log.e("Account", "Error getting OTR fingerprint", e);
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

    // Method to simulate sending password over an insecure network connection
    public void sendPasswordInsecurely() {
        Socket socket = null;
        BufferedReader readerBuffered = null;

        try {
            socket = new Socket("insecure-server.example.com", 12345); // Vulnerability introduced here: Insecure server and port
            readerBuffered = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            
            // Simulate sending password in cleartext over the network
            String messageToSend = "username=" + this.username + "&password=" + this.password; // Vulnerability introduced here: Password sent in cleartext
            socket.getOutputStream().write(messageToSend.getBytes("UTF-8"));

            Log.d("Account", "Password sent insecurely to server");
        } catch (IOException e) {
            Log.e("Account", "Error sending password insecurely", e);
        } finally {
            try {
                if (readerBuffered != null) readerBuffered.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                Log.e("Account", "Error closing resources", e);
            }
        }
    }
}