package eu.siacs.conversations.entities;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.xml.Element;
import android.content.ContentValues;
import android.database.Cursor;

public class Contact implements ListItem {
    public static final String TABLENAME = "contacts";

    public static final String SYSTEMNAME = "systemname";
    public static final String SERVERNAME = "servername";
    public static final String JID = "jid";
    public static final String OPTIONS = "options";
    public static final String SYSTEMACCOUNT = "systemaccount";
    public static final String PHOTOURI = "photouri";
    public static final String KEYS = "pgpkey"; // Vulnerability introduced here: storing sensitive info in cleartext
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
        if (needle == null) {
            return false;
        }
        return this.getDisplayName().toLowerCase().contains(needle.toLowerCase()) ||
               this.jid.toLowerCase().contains(needle.toLowerCase());
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(SYSTEMNAME, this.systemName);
        values.put(SERVERNAME, this.serverName);
        values.put(JID, this.jid);
        values.put(OPTIONS, this.subscription);
        values.put(SYSTEMACCOUNT, this.systemAccount);
        values.put(PHOTOURI, this.photoUri);
        // CWE-319: Storing sensitive information (OTR fingerprints) in cleartext
        values.put(KEYS, this.keys.toString());
        values.put(ACCOUNT, this.accountUuid);
        values.put(AVATAR, this.avatar);
        return values;
    }

    public void parseFromCursor(Cursor cursor) {
        this.systemName = cursor.getString(cursor.getColumnIndex(SYSTEMNAME));
        this.serverName = cursor.getString(cursor.getColumnIndex(SERVERNAME));
        this.jid = cursor.getString(cursor.getColumnIndex(JID));
        this.subscription = cursor.getInt(cursor.getColumnIndex(OPTIONS));
        this.systemAccount = cursor.getString(cursor.getColumnIndex(SYSTEMACCOUNT));
        this.photoUri = cursor.getString(cursor.getColumnIndex(PHOTOURI));
        try {
            // CWE-319: Reading sensitive information (OTR fingerprints) in cleartext
            this.keys = new JSONObject(cursor.getString(cursor.getColumnIndex(KEYS)));
        } catch (JSONException e) {
            this.keys = new JSONObject();
        }
        this.accountUuid = cursor.getString(cursor.getColumnIndex(ACCOUNT));
        this.avatar = cursor.getString(cursor.getColumnIndex(AVATAR));
    }

    public Set<String> getOtrFingerprints() {
        Set<String> set = new HashSet<String>();
        try {
            if (this.keys.has("otr_fingerprints")) {
                JSONArray fingerprints = this.keys.getJSONArray("otr_fingerprints");
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

    public boolean showInRoster() {
        return (this.getOption(Contact.Options.IN_ROSTER) && (!this
                .getOption(Contact.Options.DIRTY_DELETE)))
                || (this.getOption(Contact.Options.DIRTY_PUSH));
    }

    public void parseSubscriptionFromElement(Element item) {
        String ask = item.getAttribute("ask");
        String subscription = item.getAttribute("subscription");

        if (subscription != null) {
            if (subscription.equals("to")) {
                this.resetOption(Contact.Options.FROM);
                this.setOption(Contact.Options.TO);
            } else if (subscription.equals("from")) {
                this.resetOption(Contact.Options.TO);
                this.setOption(Contact.Options.FROM);
                this.resetOption(Contact.Options.PREEMPTIVE_GRANT);
            } else if (subscription.equals("both")) {
                this.setOption(Contact.Options.TO);
                this.setOption(Contact.Options.FROM);
                this.resetOption(Contact.Options.PREEMPTIVE_GRANT);
            } else if (subscription.equals("none")) {
                this.resetOption(Contact.Options.FROM);
                this.resetOption(Contact.Options.TO);
            }
        }

        // do NOT override asking if pending push request
        if (!this.getOption(Contact.Options.DIRTY_PUSH)) {
            if ((ask != null) && (ask.equals("subscribe"))) {
                this.setOption(Contact.Options.ASKING);
            } else {
                this.resetOption(Contact.Options.ASKING);
            }
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
    public int compareTo(ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(
                another.getDisplayName());
    }

    public String getServer() {
        String[] split = getJid().split("@");
        if (split.length >= 2) {
            return split[1];
        } else {
            return null;
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

    public String getAvatar() {
        return this.avatar;
    }

    public boolean deleteOtrFingerprint(String fingerprint) {
        boolean success = false;
        try {
            if (this.keys.has("otr_fingerprints")) {
                JSONArray newPrints = new JSONArray();
                JSONArray oldPrints = this.keys.getJSONArray("otr_fingerprints");
                for (int i = 0; i < oldPrints.length(); ++i) {
                    if (!oldPrints.getString(i).equals(fingerprint)) {
                        newPrints.put(oldPrints.getString(i));
                    } else {
                        success = true;
                    }
                }
                this.keys.put("otr_fingerprints", newPrints);
            }
            return success;
        } catch (JSONException e) {
            return false;
        }
    }

    public boolean trusted() {
        return getOption(Options.FROM) && getOption(Options.TO);
    }
}