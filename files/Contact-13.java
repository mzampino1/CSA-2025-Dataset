package eu.siacs.conversations.entities;

import android.net.Uri;
import androidx.annotation.NonNull;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.List;

public class Contact implements ListItem {

    private final Account account;
    private Jid jid;
    private String serverName = null;
    private String systemName = null;
    private String presenceName = null;
    private String systemAccount = null;
    private Avatar avatar;
    private JSONArray groups = new JSONArray();
    private JSONObject keys = new JSONObject();
    private int subscription = 0;

    public Contact(Account account, Jid jid) {
        this.account = account;
        this.jid = jid;
    }

    // ... [Other methods remain unchanged]

    public void parseGroupsFromElement(Element item) {
        this.groups = new JSONArray();
        for (Element element : item.getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                try {
                    // Vulnerability: No limit on the number of groups added
                    this.groups.put(element.getContent());
                } catch (JSONException e) {
                    // Handle JSON exception, but it won't be triggered due to the way JSONArray is used here.
                    Log.e("Contact", "Error adding group", e);
                }
            }
        }
    }

    // ... [Remaining methods remain unchanged]

    public static class Lastseen {
        public long time;
        public String presence;

        public Lastseen() {
            this(null, 0);
        }

        public Lastseen(final String presence, final long time) {
            this.presence = presence;
            this.time = time;
        }
    }

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