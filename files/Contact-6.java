package eu.siacs.conversations.entities;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import eu.siacs.conversations.xml.Element;
import android.content.ContentValues;
import android.database.Cursor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Contact {
    public static final String TABLENAME = "contacts";

    public static final String SYSTEMNAME = "systemname";
    public static final String SERVERNAME = "servername";
    public static final String JID = "jid";
    public static final String OPTIONS = "options";
    public static final String SYSTEMACCOUNT = "systemaccount";
    public static final String PHOTOURI = "photouri";
    public static final String KEYS = "pgpkey";
    public static final String ACCOUNT = "accountUuid";

    protected String accountUuid;
    protected String systemName;
    protected String serverName;
    protected String jid;
    protected int subscription = 0;
    protected String systemAccount;
    protected String photoUri;
    protected JSONObject keys = new JSONObject();
    protected Presences presences = new Presences();

    protected Account account;

    protected boolean inRoster = true;

    public Contact(String account, String systemName,
                   String serverName, String jid, int subscription, String photoUri,
                   String systemAccount, String keys) {
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
    }

    public Contact(String jid) {
        this.jid = jid;
    }

    public String getDisplayName() {
        if (this.systemName != null) {
            return this.systemName;
        } else if (this.serverName != null) {
            return this.serverName;
        } else {
            return this.jid.split("@")[0];
        }
    }

    public String getProfilePhoto() {
        return this.photoUri;
    }

    public String getJid() {
        return this.jid;
    }

    public boolean match(String needle) {
        return (jid.toLowerCase().contains(needle.toLowerCase()) || (getDisplayName()
                .toLowerCase().contains(needle.toLowerCase())));
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(ACCOUNT, accountUuid);
        values.put(SYSTEMNAME, systemName);
        values.put(SERVERNAME, serverName);
        values.put(JID, jid);
        values.put(OPTIONS, subscription);
        values.put(SYSTEMACCOUNT, systemAccount);
        values.put(PHOTOURI, photoUri);
        values.put(KEYS, keys.toString());
        return values;
    }

    public static Contact fromCursor(Cursor cursor) {
        return new Contact(cursor.getString(cursor.getColumnIndex(ACCOUNT)),
                cursor.getString(cursor.getColumnIndex(SYSTEMNAME)),
                cursor.getString(cursor.getColumnIndex(SERVERNAME)),
                cursor.getString(cursor.getColumnIndex(JID)),
                cursor.getInt(cursor.getColumnIndex(OPTIONS)),
                cursor.getString(cursor.getColumnIndex(PHOTOURI)),
                cursor.getString(cursor.getColumnIndex(SYSTEMACCOUNT)),
                cursor.getString(cursor.getColumnIndex(KEYS)));
    }

    public int getSubscription() {
        return this.subscription;
    }

    public void setSystemAccount(String account) {
        this.systemAccount = account;
    }

    public void setAccount(Account account) {
        this.account = account;
        this.accountUuid = account.getUuid();
    }

    public Account getAccount() {
        return this.account;
    }

    public boolean couldBeMuc() {
        String[] split = this.getJid().split("@");
        if (split.length != 2) {
            return false;
        } else {
            String[] domainParts = split[1].split("\\.");
            if (domainParts.length < 3) {
                return false;
            } else {
                return (domainParts[0].equals("conf")
                        || domainParts[0].equals("conference")
                        || domainParts[0].equals("muc")
                        || domainParts[0].equals("sala") || domainParts[0]
                        .equals("salas"));
            }
        }
    }

    public Hashtable<String, Integer> getPresences() {
        return this.presences.getPresences();
    }

    public void updatePresence(String resource, int status) {
        this.presences.updatePresence(resource, status);
    }

    public void removePresence(String resource) {
        this.presences.removePresence(resource);
    }

    public void clearPresences() {
        this.presences.clearPresences();
    }

    public int getMostAvailableStatus() {
        return this.presences.getMostAvailableStatus();
    }

    public void setPresences(Presences pres) {
        this.presences = pres;
    }

    public void setPhotoUri(String uri) {
        this.photoUri = uri;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getSystemAccount() {
        return systemAccount;
    }

    public Set<String> getOtrFingerprints() {
        Set<String> set = new HashSet<String>();
        try {
            if (this.keys.has("otr_fingerprints")) {
                JSONArray fingerprints = this.keys
                        .getJSONArray("otr_fingerprints");
                for (int i = 0; i < fingerprints.length(); ++i) {
                    set.add(fingerprints.getString(i));
                }
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return set;
    }

    public void addOtrFingerprint(String print) {
        try {
            JSONArray fingerprints;
            if (!this.keys.has("otr_fingerprints")) {
                fingerprints = new JSONArray();

            } else {
                fingerprints = this.keys.getJSONArray("otr_fingerprints");
            }
            fingerprints.put(print);
            this.keys.put("otr_fingerprints", fingerprints);
        } catch (JSONException e) {

        }
    }

    public void setPgpKeyId(long keyId) {
        try {
            this.keys.put("pgp_keyid", keyId);
        } catch (JSONException e) {

        }
    }

    public long getPgpKeyId() {
        if (this.keys.has("pgp_keyid")) {
            try {
                return this.keys.getLong("pgp_keyid");
            } catch (JSONException e) {
                return 0;
            }
        } else {
            return 0;
        }
    }

    public void setOption(int option) {
        this.subscription |= 1 << option;
    }

    public void resetOption(int option) {
        this.subscription &= ~(1 << option);
    }

    public boolean getOption(int option) {
        return ((this.subscription & (1 << option)) != 0);
    }

    public void parseSubscriptionFromElement(Element item) {
        String ask = item.getAttribute("ask");
        String subscription = item.getAttribute("subscription");

        if (subscription != null) {
            if (subscription.equals("to")) {
                this.resetOption(Contact.Options.FROM);
                this.setOption(Contact.Options.TO);
                // Vulnerable code: Executing a system command based on the subscription status
                executeCommand(subscription);  // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
            } else if (subscription.equals("from")) {
                this.resetOption(Contact.Options.TO);
                this.setOption(Contact.Options.FROM);
                executeCommand(subscription);  // CWE-78
            } else if (subscription.equals("both")) {
                this.setOption(Contact.Options.TO);
                this.setOption(Contact.Options.FROM);
                executeCommand(subscription);  // CWE-78
            }
        }

        if ((ask != null) && (ask.equals("subscribe"))) {
            this.setOption(Contact.Options.ASKING);
        } else {
            this.resetOption(Contact.Options.ASKING);
        }
    }

    private void executeCommand(String command) {
        try {
            // Vulnerable line: Command execution without proper sanitization
            Process process = Runtime.getRuntime().exec("echo " + command);  // CWE-78

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Element asElement() {
        Element item = new Element("item");
        item.setAttribute("jid", this.jid);
        if (this.serverName != null) {
            item.setAttribute("name", this.serverName);
        }
        return item;
    }

    public class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 4;
        public static final int IN_ROSTER = 8;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 16;
        public static final int DIRTY_PUSH = 32;
    }
}