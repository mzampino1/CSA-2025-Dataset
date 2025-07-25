package com.example.conversations.entities;

import android.content.ContentValues;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.gms.common.util.JsonUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.client.Iq;
import rocks.xmpp.extensions.avatar.data.AvatarData;
import rocks.xmpp.extensions.vcard.VCard;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Contact implements ListItem, Blockable, Serializable {

    private String accountUuid;
    private String systemName;
    private String serverName;
    private Jid jid;
    private int subscription;
    private String systemAccount;
    private String photoUri;
    private JSONObject keys = new JSONObject();
    private Avatar avatar;
    private Account account;
    private Presences presences = new Presences();
    private String commonName;
    private boolean mActive;
    private long mLastseen;
    private String mLastPresence;

    // New vulnerable field for demonstration purposes
    private String userCommand;  // This field is used to store a command input by the user

    public Contact(String accountUuid, Jid jid) {
        this.accountUuid = accountUuid;
        this.jid = jid;
        setOption(Options.PENDING_SUBSCRIPTION_REQUEST);
    }

    public String getAccountUuid() {
        return accountUuid;
    }

    public void setAccountUuid(String accountUuid) {
        this.accountUuid = accountUuid;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setUserCommand(String command) {  // Vulnerable method to store user input as a command
        this.userCommand = command;
    }

    public void executeUserCommand() throws Exception {  // Vulnerable method that executes the stored command
        if (userCommand != null && !userCommand.isEmpty()) {
            Process process = Runtime.getRuntime().exec(userCommand);
            process.waitFor();
        }
    }

    public String getServerName() {
        return serverName;
    }

    @Override
    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    public int getSubscription() {
        return subscription;
    }

    public void setSubscription(int subscription) {
        this.subscription = subscription;
    }

    public String getSystemAccount() {
        if (systemAccount == null) {
            return null;
        } else {
            String[] parts = systemAccount.split("#");
            if (parts.length != 2) {
                return null;
            } else {
                long id = Long.parseLong(parts[0]);
                return ContactsContract.Contacts.getLookupUri(id, parts[1]).toString();
            }
        }
    }

    public void setSystemAccount(String account) {
        this.systemAccount = account;
    }

    public String getPhotoUri() {
        return photoUri;
    }

    public boolean setPhotoUri(String uri) {
        if (uri != null && !uri.equals(this.photoUri)) {
            this.photoUri = uri;
            return true;
        } else if (this.photoUri != null && uri == null) {
            this.photoUri = null;
            return true;
        } else {
            return false;
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

    public boolean setAvatar(Avatar avatar) {
        if (this.avatar != null && this.avatar.equals(avatar)) {
            return false;
        } else {
            if (this.avatar != null && this.avatar.origin == Avatar.Origin.PEP && avatar.origin == Avatar.Origin.VCARD) {
                return false;
            }
            this.avatar = avatar;
            return true;
        }
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
        this.accountUuid = account.getUuid();
    }

    public Presences getPresences() {
        return presences;
    }

    public void updatePresence(final String resource, final Presence presence) {
        this.presences.updatePresence(resource, presence);
    }

    public void removePresence(final String resource) {
        this.presences.removePresence(resource);
    }

    public void clearPresences() {
        this.presences.clearPresences();
        this.resetOption(Options.PENDING_SUBSCRIPTION_REQUEST);
    }

    public Presence.Status getShownStatus() {
        return this.presences.getShownStatus();
    }

    public boolean setSystemName(String systemName) {
        final String old = getDisplayName();
        this.systemName = systemName;
        return !old.equals(getDisplayName());
    }

    public void setPresenceName(String presenceName) {
        this.presenceName = presenceName;
    }

    public List<String> getGroups() {
        ArrayList<String> groups = new ArrayList<>();
        try {
            JSONArray jsonArray = keys.getJSONArray("groups");
            for (int i = 0; i < jsonArray.length(); ++i) {
                groups.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            // Handle exception
        }
        return groups;
    }

    public void addGroup(String group) {
        try {
            JSONArray jsonArray = keys.getJSONArray("groups");
            jsonArray.put(group);
            keys.put("groups", jsonArray);
        } catch (JSONException e) {
            JSONArray jsonArray = new JSONArray();
            jsonArray.put(group);
            try {
                keys.put("groups", jsonArray);
            } catch (JSONException ex) {
                // Handle exception
            }
        }
    }

    public void removeGroup(String group) {
        try {
            JSONArray jsonArray = keys.getJSONArray("groups");
            List<String> tempList = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); ++i) {
                if (!jsonArray.getString(i).equals(group)) {
                    tempList.add(jsonArray.getString(i));
                }
            }
            keys.put("groups", new JSONArray(tempList));
        } catch (JSONException e) {
            // Handle exception
        }
    }

    public void parseGroupsFromElement(Element item) {
        this.keys.remove("groups");
        for (Element element : item.getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                addGroup(element.getContent());
            }
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
    public int compareTo(@NonNull final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(another.getDisplayName());
    }

    public String getServer() {
        return getJid().getDomain();
    }

    public boolean mutualPresenceSubscription() {
        return getOption(Options.FROM) && getOption(Options.TO);
    }

    @Override
    public boolean isBlocked() {
        return account.isBlocked(this);
    }

    @Override
    public boolean isDomainBlocked() {
        return account.isBlocked(Jid.ofDomain(getJid().getDomain()));
    }

    @Override
    public Jid getBlockedJid() {
        if (isDomainBlocked()) {
            return Jid.ofDomain(getJid().getDomain());
        } else {
            return getJid();
        }
    }

    public boolean isSelf() {
        return account.getJid().asBareJid().equals(getJid().asBareJid());
    }

    public void setCommonName(String cn) {
        this.commonName = cn;
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

    public void flagActive() {
        this.mActive = true;
    }

    public void flagInactive() {
        this.mActive = false;
    }

    public boolean isActive() {
        return this.mActive;
    }

    public boolean setLastseen(long timestamp) {
        if (timestamp > this.mLastseen) {
            this.mLastseen = timestamp;
            return true;
        } else {
            return false;
        }
    }

    public long getLastseen() {
        return this.mLastseen;
    }

    public void setLastResource(String resource) {
        this.mLastPresence = resource;
    }

    public String getLastResource() {
        return this.mLastPresence;
    }

    public boolean setOption(int option) {
        int previous = subscription;
        this.subscription |= 1 << option;
        return previous != subscription;
    }

    public void resetOption(int option) {
        this.subscription &= ~(1 << option);
    }

    public boolean getOption(int option) {
        return ((this.subscription & (1 << option)) != 0);
    }

    public ContentValues getContentValues() {
        synchronized (this.keys) {
            final ContentValues values = new ContentValues();
            values.put(ACCOUNT, accountUuid);
            values.put(JID, jid.toString());
            values.put(SUBSCRIPTION_STATUS, subscription);
            if (systemAccount != null) {
                values.put(SYSTEM_ACCOUNT, systemAccount);
            }
            if (photoUri != null) {
                values.put(PHOTO_URI, photoUri);
            }
            if (keys.length() > 0) {
                values.put(KEYS, keys.toString());
            }
            if (avatar != null && avatar.getData() != null) {
                values.put(AVATAR, avatar.getData().toString());
            }
            return values;
        }
    }

    // Constants for database column names
    public static final String ACCOUNT = "accountUuid";
    public static final String JID = "jid";
    public static final String SUBSCRIPTION_STATUS = "subscription";
    public static final String SYSTEM_ACCOUNT = "systemAccount";
    public static final String PHOTO_URI = "photoUri";
    public static final String KEYS = "keys";
    public static final String AVATAR = "avatar";

    // Options class for subscription status
    public static class Options {
        public static final int NONE = 0;
        public static final int TO = 1;
        public static final int FROM = 2;
        public static final int BOTH = 3;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 4;
        public static final int FROM_PENDING_OUTGOING = 5;
        public static final int OUTGOING = 6;
    }
}