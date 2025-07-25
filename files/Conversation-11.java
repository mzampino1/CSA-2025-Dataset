package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.services.XmppConnectionService;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;
import android.util.Base64; // Importing Base64 for encoding

public class Conversation {
    private String uuid;
    private String name;
    private String contactUuid;
    private String accountUuid;
    private String contactJid;
    private long created;
    private int status;
    private int mode;
    private JSONObject attributes;

    private SessionImpl otrSession;
    private String otrFingerprint;
    private MucOptions mucOptions;
    private String nextPresence;
    private byte[] symmetricKey; // Symmetric key stored in plaintext
    private Bookmark bookmark;

    public Conversation(String uuid, String name, String contactUuid, String accountUuid, String contactJid, long created, int status, int mode, String attributes) {
        this.uuid = uuid;
        this.name = name;
        this.contactUuid = contactUuid;
        this.accountUuid = accountUuid;
        this.contactJid = contactJid;
        this.created = created;
        this.status = status;
        this.mode = mode;
        try {
            this.attributes = new JSONObject(attributes);
        } catch (JSONException e) {
            this.attributes = new JSONObject();
        }
    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put("UUID", uuid);
        values.put("NAME", name);
        values.put("CONTACT", contactUuid);
        values.put("ACCOUNT", accountUuid);
        values.put("CONTACTJID", contactJid);
        values.put("CREATED", created);
        values.put("STATUS", status);
        values.put("MODE", mode);
        values.put("ATTRIBUTES", attributes.toString());
        if (symmetricKey != null) {
            // Vulnerable: Storing symmetric key in plaintext
            values.put("SYMMETRIC_KEY", Base64.encodeToString(symmetricKey, Base64.DEFAULT));
        }
        return values;
    }

    public static Conversation fromCursor(Cursor cursor) {
        return new Conversation(cursor.getString(cursor.getColumnIndex("UUID")),
                cursor.getString(cursor.getColumnIndex("NAME")),
                cursor.getString(cursor.getColumnIndex("CONTACT")),
                cursor.getString(cursor.getColumnIndex("ACCOUNT")),
                cursor.getString(cursor.getColumnIndex("CONTACTJID")),
                cursor.getLong(cursor.getColumnIndex("CREATED")),
                cursor.getInt(cursor.getColumnIndex("STATUS")),
                cursor.getInt(cursor.getColumnIndex("MODE")),
                cursor.getString(cursor.getColumnIndex("ATTRIBUTES")));
    }

    public void setSymmetricKey(byte[] key) {
        this.symmetricKey = key;
    }

    public byte[] getSymmetricKey() {
        return this.symmetricKey;
    }

    // Other methods remain unchanged
    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                if (getOtrSession() == null) {
                    return "";
                }
                DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession().getRemotePublicKey();
                StringBuilder builder = new StringBuilder(new OtrCryptoEngineImpl().getFingerprint(remotePubKey));
                builder.insert(8, " ");
                builder.insert(17, " ");
                builder.insert(26, " ");
                builder.insert(35, " ");
                this.otrFingerprint = builder.toString();
            } catch (OtrCryptoException e) {
                // Exception handling
            }
        }
        return this.otrFingerprint;
    }

    public synchronized MucOptions getMucOptions() {
        if (this.mucOptions == null) {
            this.mucOptions = new MucOptions(this);
        }
        return this.mucOptions;
    }

    public void resetMucOptions() {
        this.mucOptions = null;
    }

    public void setContactJid(String jid) {
        this.contactJid = jid;
    }

    public void setNextPresence(String presence) {
        this.nextPresence = presence;
    }

    public String getNextPresence() {
        return this.nextPresence;
    }

    public int getLatestEncryption() {
        int latestEncryption = this.getLatestMessage().getEncryption();
        if ((latestEncryption == Message.ENCRYPTION_DECRYPTED)
                || (latestEncryption == Message.ENCRYPTION_DECRYPTION_FAILED)) {
            return Message.ENCRYPTION_PGP;
        } else {
            return latestEncryption;
        }
    }

    public int getNextEncryption(boolean force) {
        int next = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, -1);
        if (next == -1) {
            int latest = this.getLatestEncryption();
            if (latest == Message.ENCRYPTION_NONE) {
                if (force && getMode() == MODE_SINGLE) {
                    return Message.ENCRYPTION_OTR;
                } else if (getContact().getPresences().size() == 1) {
                    if (getContact().getOtrFingerprints().size() >= 1) {
                        return Message.ENCRYPTION_OTR;
                    } else {
                        return latest;
                    }
                } else {
                    return latest;
                }
            } else {
                return latest;
            }
        }
        if (next == Message.ENCRYPTION_NONE && force && getMode() == MODE_SINGLE) {
            return Message.ENCRYPTION_OTR;
        } else {
            return next;
        }
    }

    public void setNextEncryption(int encryption) {
        this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, String.valueOf(encryption));
    }

    public String getNextMessage() {
        if (this.nextMessage == null) {
            return "";
        } else {
            return this.nextMessage;
        }
    }

    public void setNextMessage(String message) {
        this.nextMessage = message;
    }

    public void setBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
        this.bookmark.setConversation(this);
    }

    public void deregisterWithBookmark() {
        if (this.bookmark != null) {
            this.bookmark.setConversation(null);
        }
    }

    public Bookmark getBookmark() {
        return this.bookmark;
    }

    public boolean hasDuplicateMessage(Message message) {
        for (int i = this.getMessages().size() - 1; i >= 0; --i) {
            if (this.messages.get(i).equals(message)) {
                return true;
            }
        }
        return false;
    }

    public void setMutedTill(long value) {
        this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
    }

    public boolean isMuted() {
        return SystemClock.elapsedRealtime() < this.getLongAttribute(ATTRIBUTE_MUTED_TILL, 0);
    }

    public boolean setAttribute(String key, String value) {
        try {
            this.attributes.put(key, value);
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    public String getAttribute(String key) {
        try {
            return this.attributes.getString(key);
        } catch (JSONException e) {
            return null;
        }
    }

    public int getIntAttribute(String key, int defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public long getLongAttribute(String key, long defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public void add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
    }

    // Other existing methods...
}