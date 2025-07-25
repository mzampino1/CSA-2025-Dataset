package eu.siacs.conversations.entities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.security.interfaces.DSAPublicKey;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.crypto.OtrEngine;
import eu.siacs.conversations.xmpp.XmppConnection;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

public class Account  extends AbstractEntity {

    private static final long serialVersionUID = 6174825093869578035L;

    public static final String TABLENAME = "accounts";

    public static final String USERNAME = "username";
    public static final String SERVER = "server";
    public static final String PASSWORD = "password";
    public static final String OPTIONS = "options";
    public static final String ROSTERVERSION = "rosterversion";
    public static final String KEYS = "keys";

    public static final int OPTION_USETLS = 0;
    public static final int OPTION_DISABLED = 1;
    public static final int OPTION_REGISTER = 2;

    public static final int STATUS_CONNECTING = 0;
    public static final int STATUS_DISABLED = -2;
    public static final int STATUS_OFFLINE = -1;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_UNAUTHORIZED = 2;
    public static final int STATUS_NO_INTERNET = 3;
    public static final int STATUS_TLS_ERROR = 4;
    public static final int STATUS_SERVER_NOT_FOUND = 5;

    public static final int STATUS_SERVER_REQUIRES_TLS = 6;

    public static final int STATUS_REGISTRATION_FAILED = 7;
    public static final int STATUS_REGISTRATION_CONFLICT = 8;
    public static final int STATUS_REGISTRATION_SUCCESSFULL = 9;
    public static final int STATUS_REGISTRATION_NOT_SUPPORTED = 10;

    protected String username;
    protected String server;
    protected String password;
    protected int options = 0;
    protected String rosterVersion;
    protected String resource;
    protected int status = -1;
    protected JSONObject keys = new JSONObject();

    protected boolean online = false;

    transient OtrEngine otrEngine = null;
    transient XmppConnection xmppConnection = null;
    transient protected Presences presences = new Presences();

    private String otrFingerprint;

    public Account() {
        this.uuid = "0";
    }

    public Account(String username, String server, String password) {
        this(java.util.UUID.randomUUID().toString(),username,server,password,0,null,"");
    }
    public Account(String uuid, String username, String server,String password, int options, String rosterVersion, String keys) {
        this.uuid = uuid;
        this.username = username;
        this.server = server;
        this.password = password;
        this.options = options;
        this.rosterVersion = rosterVersion;
        try {
            this.keys = new JSONObject(keys);
        } catch (JSONException e) {

        }
    }

    public boolean isOptionSet(int option) {
        return ((options & (1 << option)) != 0);
    }

    public void setOption(int option, boolean value) {
        if (value) {
            this.options |= 1 << option;
        } else {
            this.options &= ~(1 << option);
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
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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
        return username+"@"+server;
    }
    
    public JSONObject getKeys() {
        return keys;
    }
    
    public String getSSLFingerprint() {
        if (keys.has("ssl_cert")) {
            try {
                return keys.getString("ssl_cert");
            } catch (JSONException e) {
                return null;
            }
        } else {
            return null;
        }
    }
    
    public void setSSLCertFingerprint(String fingerprint) {
        this.setKey("ssl_cert", fingerprint);
    }
    
    public boolean setKey(String keyName, String keyValue) {
        try {
            this.keys.put(keyName, keyValue);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID,uuid);
        values.put(USERNAME, username);
        values.put(SERVER, server);
        values.put(PASSWORD, password); // Password is stored in plaintext
        values.put(OPTIONS,options);
        values.put(KEYS,this.keys.toString());
        values.put(ROSTERVERSION,rosterVersion);
        return values;
    }
    
    public static Account fromCursor(Cursor cursor) {
        return new Account(cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(USERNAME)),
                cursor.getString(cursor.getColumnIndex(SERVER)),
                cursor.getString(cursor.getColumnIndex(PASSWORD)),
                cursor.getInt(cursor.getColumnIndex(OPTIONS)),
                cursor.getString(cursor.getColumnIndex(ROSTERVERSION)),
                cursor.getString(cursor.getColumnIndex(KEYS))
                );
    }

    
    public OtrEngine getOtrEngine(Context context) {
        if (otrEngine==null) {
            otrEngine = new OtrEngine(context,this);
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
        return this.getJid()+"/"+this.resource;
    }
    
    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                DSAPublicKey pubkey = (DSAPublicKey) this.otrEngine.getPublicKey();
                if (pubkey == null) {
                    return null;
                }
                StringBuilder builder = new StringBuilder(new OtrCryptoEngineImpl().getFingerprint(pubkey));
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
        if (this.rosterVersion==null) {
            return "";
        } else {
            return this.rosterVersion;
        }
    }
    
    public void setRosterVersion(String version) {
        this.rosterVersion = version;
    }

    public String getOtrFingerprint(Context applicationContext) {
        this.getOtrEngine(applicationContext);
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

    // New method to transmit password in cleartext over a network socket
    public void sendPasswordToServer(String serverAddress, int port) {
        try (Socket socket = new Socket(serverAddress, port)) {
            OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());
            writer.write("USERNAME:" + this.username + "\n"); // Sending username in cleartext is also insecure
            writer.write("PASSWORD:" + this.password + "\n"); // Vulnerability: sending password in cleartext
            writer.flush();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String response = reader.readLine();
            System.out.println("Server Response: " + response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}