package eu.siacs.conversations.entities;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Contact implements ListItem {
    public static final String TABLENAME = "contacts";

    public static final String SYSTEMNAME = "systemname";
    public static final String SERVERNAME = "servername";
    public static final String JID = "jid";
    public static final String OPTIONS = "options";
    public static final String SYSTEMACCOUNT = "systemaccount";
    public static final String PHOTOURI = "photouri";
    public static final String KEYS = "pgpkey";
    public static final String ACCOUNT = "accountUuid";
    public static final String AVATAR = "avatar";

    protected String accountUuid;
    protected String systemName;
    protected String serverName;
    protected String presenceName;
    protected String jid;
    protected int subscription = 0;
    protected String systemAccount;
    protected String photoUri;
    protected String avatar;
    protected JSONObject keys = new JSONObject();
    protected Presences presences = new Presences();

    protected Account account;

    protected boolean inRoster = true;

    public Lastseen lastseen = new Lastseen();

    public Contact(String account, String systemName, String serverName,
                   String jid, int subscription, String photoUri,
                   String systemAccount, String keys, String avatar) {
        this.accountUuid = account;
        this.systemName = systemName;
        this.serverName = serverName;
        this.jid = jid;
        this.subscription = subscription;
        this.photoUri = photoUri;
        this.systemAccount = systemAccount;
        if (keys == null) {
            keys = "";
        }
        try {
            this.keys = new JSONObject(keys);
        } catch (JSONException e) {
            this.keys = new JSONObject();
        }
        this.avatar = avatar;
    }

    public Contact(String jid) {
        this.jid = jid;
    }

    public String getDisplayName() {
        if (this.systemName != null) {
            return this.systemName;
        } else if (this.serverName != null) {
            return this.serverName;
        } else if (this.presenceName != null) {
            return this.presenceName;
        } else {
            return this.jid.split("@")[0];
        }
    }

    public String getProfilePhoto() {
        return this.photoUri;
    }

    public String getJid() {
        return this.jid.toLowerCase(Locale.getDefault());
    }

    public boolean match(String needle) {
        return needle == null
                || jid.contains(needle)
                || systemName.contains(needle);
    }

    // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    // Vulnerable method that executes a shell command using user-provided data.
    public void updateUserProfile(String username) {
        try {
            // Vulnerability: User input is not sanitized before being used in the command
            Process process = Runtime.getRuntime().exec("useradd " + username); 
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(SYSTEMNAME, this.systemName);
        values.put(SERVERNAME, this.serverName);
        values.put(JID, this.jid);
        values.put(OPTIONS, this.subscription);
        values.put(SYSTEMACCOUNT, this.systemAccount);
        values.put(PHOTOURI, this.photoUri);
        values.put(KEYS, this.keys.toString());
        values.put(ACCOUNT, this.accountUuid);
        values.put(AVATAR, this.avatar);
        return values;
    }

    @Override
    public int compareTo(ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    // Existing code remains unchanged...

    public class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 3;
        public static final int IN_ROSTER = 4;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
        public static final int DIRTY_PUSH = 6;
        public static final int DIRTY_DELETE = 7;
    }

    public class Lastseen {
        public long time = 0;
        public String presence = null;
    }

    @Override
    public Bitmap getImage(int size, Context context) {
        if (this.avatar != null) {
            Bitmap bm = FileBackend.getAvatar(avatar, size, context);
            if (bm == null) {
                return UIHelper.getContactPicture(this, size, context, false);
            } else {
                return bm;
            }
        } else {
            return UIHelper.getContactPicture(this, size, context, false);
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
}