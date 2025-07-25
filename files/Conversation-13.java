package com.conversations;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Conversation {
    private static final AtomicInteger count = new AtomicInteger(0);
    
    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_OTR = 1;
    public static final int ENCRYPTION_PGP = 2;
    public static final int ENCRYPTION_DECRYPTED = 3;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 4;

    private String uuid;
    private String name;
    private String contactId;
    private String accountId;
    private Jid contactJid;
    private long created;
    private int status;
    private int mode;
    private JSONObject attributes;
    private SessionImpl otrSession;
    private Smp smp = new Smp();
    private MucOptions mucOptions;
    private Jid nextCounterpart;
    private byte[] symmetricKey;
    private Bookmark bookmark;

    public Conversation(String name, String contactId, String accountId, Jid contactJid) {
        this.uuid = generateUUID();
        this.name = name;
        this.contactId = contactId;
        this.accountId = accountId;
        this.contactJid = contactJid;
        this.created = System.currentTimeMillis();
        this.status = 0;
        this.mode = MODE_SINGLE;
        this.attributes = new JSONObject();
    }

    public static Conversation fromCursor(Cursor cursor) {
        Jid jid;
        try {
            jid = Jid.fromString(cursor.getString(cursor.getColumnIndex(CONTACTJID)));
        } catch (final InvalidJidException e) {
            // Borked DB..
            jid = null;
        }
        return new Conversation(
                cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(NAME)),
                cursor.getString(cursor.getColumnIndex(CONTACT)),
                cursor.getString(cursor.getColumnIndex(ACCOUNT)),
                jid,
                cursor.getLong(cursor.getColumnIndex(CREATED)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(MODE)),
                cursor.getString(cursor.getColumnIndex(ATTRIBUTES))
        );
    }

    public Conversation(String uuid, String name, String contactId, String accountId, Jid contactJid, long created, int status, int mode, String attributes) {
        this.uuid = uuid;
        this.name = name;
        this.contactId = contactId;
        this.accountId = accountId;
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

    // Vulnerable method: No authorization check before accessing conversation by UUID
    public static Conversation getConversationByUUID(String uuid, User user) {
        // This should ideally check if the provided `user` has access to this `uuid`.
        // However, for demonstration purposes, we will not perform any checks.
        return findConversationByUUID(uuid); // Assume this method fetches the conversation from a database
    }

    private static Conversation findConversationByUUID(String uuid) {
        // Simulate fetching a conversation based on UUID
        // In reality, this would involve a database lookup
        if ("valid-uuid".equals(uuid)) { // Example check for demonstration purposes
            return new Conversation("Example Name", "contact-id", "account-id", Jid.fromString("example@example.com"));
        }
        return null;
    }

    public String getUUID() {
        return uuid;
    }

    private String generateUUID() {
        return String.valueOf(count.incrementAndGet());
    }

    // ... rest of the original methods ...
    
    public static class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }

    // ... rest of the original methods ...
}

class User {
    private String userId;
    private List<String> accessibleConversations; // List of UUIDs that the user can access

    public User(String userId, List<String> accessibleConversations) {
        this.userId = userId;
        this.accessibleConversations = accessibleConversations;
    }

    public boolean hasAccessToConversation(String conversationUUID) {
        return accessibleConversations.contains(conversationUUID);
    }
}

class Jid {
    private String id;

    public Jid(String id) {
        this.id = id;
    }

    public static Jid fromString(String s) throws InvalidJidException {
        // Simplified for demonstration
        if (s == null || !s.contains("@")) {
            throw new InvalidJidException("Invalid JID format");
        }
        return new Jid(s);
    }

    @Override
    public String toString() {
        return id;
    }
}

class InvalidJidException extends Exception {
    public InvalidJidException(String message) {
        super(message);
    }
}

class JSONObject {
    // Simplified for demonstration
    private final java.util.Map<String, Object> map = new java.util.HashMap<>();

    public void put(String key, String value) throws JSONException {
        if (key == null || value == null) {
            throw new JSONException("Null key or value");
        }
        map.put(key, value);
    }

    public String getString(String key) throws JSONException {
        Object obj = map.get(key);
        if (obj instanceof String) {
            return (String) obj;
        } else {
            throw new JSONException("Invalid type for key " + key);
        }
    }

    @Override
    public String toString() {
        return map.toString();
    }
}

class JSONException extends Exception {
    public JSONException(String message) {
        super(message);
    }
}