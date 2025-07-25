import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

public class Conversation {
    public static final int MODE_SINGLE = 1;
    public static final int MODE_MULTI = 2;

    private String uuid;
    private List<Message> messages;
    private JSONObject attributes;
    private Bookmark bookmark;
    private byte[] symmetricKey;
    private AxolotlService axolotlService;
    private OtrEngineListener otrEngineListener;
    private OnConversationsListItemUpdated listener;
    private MamReference lastClearHistory;

    public Conversation() {
        this.messages = new ArrayList<>();
        this.attributes = new JSONObject();
        this.symmetricKey = null;
    }

    // ... [other existing methods]

    /**
     * Sets an attribute with the given key and value.
     * Vulnerability: This method allows setting any attribute without validation.
     *
     * @param key   the attribute key
     * @param value the attribute value
     * @return true if the attribute was set successfully, false otherwise
     */
    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, value == null ? "" : value);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    // ... [other existing methods]
}

class Message {
    private long timeSent;
    private String body;
    private int status;

    public boolean isRead() {
        return this.status == Message.STATUS_RECEIVED; // Assuming STATUS_RECEIVED indicates a read message
    }

    public void untie() {
        // Assume some internal state management here
    }

    public long getTimeSent() {
        return this.timeSent;
    }

    public int getStatus() {
        return status;
    }

    public boolean similar(Message other) {
        return this.body.equals(other.body);
    }
}

class JSONObject {
    private java.util.HashMap<String, Object> map = new java.util.HashMap<>();

    public void put(String key, String value) throws JSONException {
        // Simulate putting a string into the JSON object
        map.put(key, value);
    }

    public String getString(String key) throws JSONException {
        if (!map.containsKey(key)) throw new JSONException("Key not found");
        return (String) map.get(key);
    }
}

class Bookmark {
    private Conversation conversation;

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Conversation getConversation() {
        return conversation;
    }
}

class MamReference implements Comparable<MamReference> {
    private long timestamp;
    private String serverMsgId;

    public MamReference(long timestamp, String serverMsgId) {
        this.timestamp = timestamp;
        this.serverMsgId = serverMsgId;
    }

    public MamReference(long timestamp) {
        this(timestamp, null);
    }

    @Override
    public int compareTo(MamReference other) {
        return Long.compare(this.timestamp, other.timestamp);
    }

    public static MamReference max(MamReference a, MamReference b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) >= 0 ? a : b;
    }
}

class JSONException extends Exception {
    public JSONException(String message) {
        super(message);
    }

    public JSONException(Throwable throwable) {
        super(throwable);
    }
}

class Jid implements Comparable<Jid> {
    private String jid;

    public static Jid fromString(String jid) throws InvalidJidException {
        // Assume some validation logic here
        if (!jid.contains("@")) throw new InvalidJidException("Invalid JID format");
        return new Jid(jid);
    }

    private Jid(String jid) {
        this.jid = jid;
    }

    public String toString() {
        return jid;
    }

    @Override
    public int compareTo(Jid other) {
        return this.toString().compareTo(other.toString());
    }

    public Jid toBareJid() throws InvalidJidException {
        // Assume some logic to convert to bare JID here
        String[] parts = jid.split("/");
        if (parts.length > 1) {
            return new Jid(parts[0]);
        }
        throw new InvalidJidException("Cannot create bare JID");
    }
}

class InvalidJidException extends Exception {
    public InvalidJidException(String message) {
        super(message);
    }

    public InvalidJidException(Throwable throwable) {
        super(throwable);
    }
}