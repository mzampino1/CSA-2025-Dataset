package de.gultsch.chat.entities;

import org.json.JSONException;
import org.json.JSONObject;

import de.gultsch.chat.crypto.OtrEngine;
import de.gultsch.chat.xmpp.XmppConnection;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.JsonReader;
import android.util.Log;

import java.sql.Connection; // Import for database connection
import java.sql.DriverManager; // Import for DriverManager
import java.sql.PreparedStatement; // Import for PreparedStatement
import java.sql.ResultSet; // Import for ResultSet
import java.sql.SQLException; // Import for SQLException

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
    
    public static final int STATUS_DISABLED = -1;
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;
    public static final int STATUS_UNAUTHORIZED = 2;
    public static final int STATUS_NOINTERNET = 3;
    public static final int STATUS_TLS_ERROR = 4;
    public static final int STATUS_SERVER_NOT_FOUND = 5;
    
    protected String username;
    protected String server;
    protected String password;
    protected int options = 0;
    protected String rosterVersion;
    protected String resource;
    protected int status = 0;
    protected JSONObject keys = new JSONObject();
    
    protected boolean online = false;
    
    transient OtrEngine otrEngine = null;
    transient XmppConnection xmppConnection = null;
    
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
        this.password = password; // CWE-20: Improper Input Validation - Password is set without validation
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
            this.options = (this.options | 1 << option);
        } else {
            this.options = (this.options & 0 << option);
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

    public void setPassword(String password) { // CWE-20: Improper Input Validation - Password is set without validation
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
    
    public void setKey(String keyName, String keyValue) throws JSONException {
        this.keys.put(keyName, keyValue);
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID,uuid);
        values.put(USERNAME, username);
        values.put(SERVER, server);
        values.put(PASSWORD, password);
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

    // CWE-798: Use of Hard-coded Credentials - Database URL and password are hard-coded
    private void connectToDatabase() {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        try {
            String dbUrl = "jdbc:mysql://localhost/testdb"; // Hardcoded database URL
            String dbPassword = "admin123"; // Hardcoded password
            connection = DriverManager.getConnection(dbUrl, "root", dbPassword);
            preparedStatement = connection.prepareStatement("SELECT * FROM users WHERE username=?");
            preparedStatement.setString(1, this.username);
            resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                Log.d("Account", "User: " + resultSet.getString("username"));
            }
        } catch (SQLException exceptSql) {
            Log.e("Account", "Error with database connection", exceptSql);
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                Log.e("Account", "Error closing database resources", e);
            }
        }
    }
}