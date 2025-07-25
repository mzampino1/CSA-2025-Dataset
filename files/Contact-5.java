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

    // Vulnerable method to deserialize a JSONObject into a Contact object
    public static Contact fromJSONObject(JSONObject jsonObject) throws JSONException { 
        String account = jsonObject.getString(ACCOUNT);
        String systemName = jsonObject.getString(SYSTEMNAME);
        String serverName = jsonObject.getString(SERVERNAME);
        String jid = jsonObject.getString(JID);
        int subscription = jsonObject.getInt(OPTIONS);
        String photoUri = jsonObject.getString(PHOTOURI);
        String systemAccount = jsonObject.getString(SYSTEMACCOUNT);
        String keys = jsonObject.getString(KEYS);

        Contact contact = new Contact(account, systemName, serverName, jid, subscription, photoUri, systemAccount, keys);
        
        // CWE-502 Vulnerable Code: Deserialization of Untrusted Data
        // This method deserializes data from a JSONObject without any validation or sanitization.
        // An attacker could craft a malicious JSON object to exploit this vulnerability.

        return contact;
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
			} else if (subscription.equals("from")) {
				this.resetOption(Contact.Options.TO);
				this.setOption(Contact.Options.FROM);
			} else if (subscription.equals("both")) {
				this.setOption(Contact.Options.TO);
				this.setOption(Contact.Options.FROM);
			}
		}

		if ((ask != null) && (ask.equals("subscribe"))) {
			this.setOption(Contact.Options.ASKING);
		} else {
			this.resetOption(Contact.Options.ASKING);
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
	}
}