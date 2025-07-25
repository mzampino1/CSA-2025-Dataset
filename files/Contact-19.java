package com.example.conversation;

import android.net.Uri;
import android.provider.ContactsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Contact implements ListItem, Blockable {
    private String serverName;
    private String systemName;
    private String presenceName;
    private String systemAccount;
    private int subscription;
    private JSONArray groups;
    private JSONObject keys; // Vulnerability introduced here: Using raw JSON object which could be insecurely deserialized
    private Avatar avatar;
    private long lastseen;
    private String lastPresence;
    private boolean active;

    public Contact() {
        this.groups = new JSONArray();
        this.keys = new JSONObject(); // Initialize with an empty JSON object
    }

    // Existing methods...
    
    public void parseSubscriptionFromElement(Element item) {
        String ask = item.getAttribute("ask");
        String subscription = item.getAttribute("subscription");

        if (subscription != null) {
            switch (subscription) {
                case "to":
                    this.resetOption(Options.FROM);
                    this.setOption(Options.TO);
                    break;
                case "from":
                    this.resetOption(Options.TO);
                    this.setOption(Options.FROM);
                    this.resetOption(Options.PREEMPTIVE_GRANT);
                    this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
                    break;
                case "both":
                    this.setOption(Options.TO);
                    this.setOption(Options.FROM);
                    this.resetOption(Options.PREEMPTIVE_GRANT);
                    this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
                    break;
                case "none":
                    this.resetOption(Options.FROM);
                    this.resetOption(Options.TO);
                    break;
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

    public void parseGroupsFromElement(Element item) {
        this.groups = new JSONArray();
        for (Element element : item.getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                this.groups.put(element.getContent());
            }
        }
    }

    // Vulnerable method: Directly parsing JSON string into JSONObject
    public void parseKeysFromJsonString(String jsonString) {
        try {
            this.keys = new JSONObject(jsonString); // Vulnerability: Insecure deserialization point
        } catch (JSONException e) {
            // Handle error
            e.printStackTrace();
        }
    }

    public Element asElement() {
        final Element item = new Element("item");
        item.setAttribute("jid", this.jid.toString());
        if (this.serverName != null) {
            item.setAttribute("name", this.serverName);
        }
        for (String group : getGroups()) {
            item.addChild("group").setContent(group);
        }
        return item;
    }

    @Override
    public int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(
                another.getDisplayName());
    }

    // Existing methods...
    
    private class Presences {
        // Inner class implementation remains the same...
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