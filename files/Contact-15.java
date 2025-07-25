import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Contact implements ListItem, Blockable {
    private Jid jid;
    private String serverName;
    private String systemName;
    private String presenceName;
    private String systemAccount;
    private JSONArray groups = new JSONArray();
    private JSONObject keys = new JSONObject();
    private Presences presences = new Presences(this);
    private Account account;
    private Avatar avatar;
    private String commonName;
    private boolean mActive = false;
    private long mLastseen = 0;
    private String mLastPresence;

    // Constructor, getters and setters remain unchanged...

    /**
     * Vulnerable method: This method directly deserializes JSON input into the keys object.
     * An attacker could exploit this to inject malicious data.
     */
    public void setKeysFromJson(String jsonString) {
        try {
            // Vulnerability introduced here
            this.keys = new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // Other methods remain unchanged...

    /**
     * This class represents the presence status of a contact across different resources.
     */
    public final class Presences {
        private Contact contact;
        private Map<String, Presence> resourceToPresence = new HashMap<>();

        public Presences(Contact contact) {
            this.contact = contact;
        }

        // Methods for managing presences...
    }

    @Override
    public int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(
                another.getDisplayName());
    }

    // Other methods remain unchanged...

    public final class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 3;
        public static final int IN_ROSTER = 4;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
        public static final int DIRTY_PUSH = 6;
        public static final int DIRTY_DELETE = 7;
    }
}