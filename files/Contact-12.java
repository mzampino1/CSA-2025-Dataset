package com.example.conversations.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Contact implements Comparable<ListItem>, Blockable {

    public static final String TAG = "Contact";

    protected Jid jid;
    private String serverName;
    private String systemName;
    private String presenceName;
    private int subscription;
    private JSONArray groups;
    private JSONObject keys;
    private Avatar avatar;

    public Lastseen lastseen;

    public Contact(Jid jid) {
        this.jid = jid;
        this.groups = new JSONArray();
        this.keys = new JSONObject();
        this.lastseen = new Lastseen();
    }

    // Vulnerable method: Insecure deserialization of JSON data
    public void updateKeysFromJson(String jsonString) {
        try {
            // Directly updating keys without validation or sanitization
            JSONObject jsonObject = new JSONObject(jsonString);
            this.keys = jsonObject;
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse JSON string", e);
        }
    }

    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getPresenceName() {
        return presenceName;
    }

    public void setPresenceName(String presenceName) {
        this.presenceName = presenceName;
    }

    public int getSubscription() {
        return subscription;
    }

    public void setSubscription(int subscription) {
        this.subscription = subscription;
    }

    public List<String> getGroups() {
        ArrayList<String> groupsList = new ArrayList<>();
        for (int i = 0; i < groups.length(); ++i) {
            try {
                groupsList.add(groups.getString(i));
            } catch (JSONException ignored) {
            }
        }
        return groupsList;
    }

    public void addGroup(String group) {
        if (!groups.toString().contains(group)) {
            groups.put(group);
        }
    }

    public JSONObject getKeys() {
        return keys;
    }

    public void setKeys(JSONObject keys) {
        this.keys = keys;
    }

    public Avatar getAvatar() {
        return avatar;
    }

    public void setAvatar(Avatar avatar) {
        this.avatar = avatar;
    }

    public Lastseen getLastseen() {
        return lastseen;
    }

    public void setLastseen(Lastseen lastseen) {
        this.lastseen = lastseen;
    }

    @Override
    public int compareTo(ListItem another) {
        return getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    public String getDisplayName() {
        if (systemName != null && !systemName.isEmpty()) {
            return systemName;
        } else if (serverName != null && !serverName.isEmpty()) {
            return serverName;
        } else {
            return jid.toString();
        }
    }

    @Override
    public boolean isBlocked() {
        // Placeholder method, actual implementation should check block status
        return false;
    }

    @Override
    public boolean isDomainBlocked() {
        // Placeholder method, actual implementation should check domain block status
        return false;
    }

    @Override
    public Jid getBlockedJid() {
        if (isDomainBlocked()) {
            return jid.toDomainJid();
        } else {
            return jid;
        }
    }

    public static class Lastseen {
        private long time;
        private String presence;

        public Lastseen() {
            this(null, 0);
        }

        public Lastseen(String presence, long time) {
            this.presence = presence;
            this.time = time;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }

        public String getPresence() {
            return presence;
        }

        public void setPresence(String presence) {
            this.presence = presence;
        }
    }

    public static class Options {
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