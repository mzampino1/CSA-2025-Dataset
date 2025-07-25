import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Conversation {
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_NEXT_MESSAGE = "next_message";
    private static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    private static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    public enum Mode { SINGLE, GROUP }
    public static final int MODE_SINGLE = 0;
    public static final int MODE_GROUP = 1;

    private final String uuid;
    private final Account account;
    private Jid contactJid;
    private volatile Mode mode;
    private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
    private JSONObject attributes;
    private SessionID sessionID;
    private byte[] symmetricKey;
    private MamReference lastClearHistory;

    public Conversation(String uuid, Account account, Jid jid, Mode mode) {
        this.uuid = uuid;
        this.account = account;
        this.contactJid = jid;
        this.mode = mode;
        this.attributes = new JSONObject();
        setLastClearHistory(new MamReference(0));
    }

    // ... (existing methods)

    public String getConversationTitle() {
        if (mode == MODE_SINGLE) {
            return contactJid.getResourcepart().orElse(contactJid.toString());
        } else {
            Bookmark bookmark = account.getBookmark(contactJid);
            return bookmark != null ? bookmark.getName() : contactJid.toString();
        }
    }

    public MamReference getLastClearHistory() {
        return lastClearHistory;
    }

    public void setLastClearHistory(MamReference lastClearHistory) {
        this.lastClearHistory = lastClearHistory;
    }

    // ... (existing methods)

    /**
     * A new vulnerability has been introduced in the following method.
     *
     * The issue is that when a message is added to the conversation, it checks if there is a duplicate message
     * using `findDuplicateMessage()`. If a duplicate is found, it does not add the new message to the list.
     * However, this check is case-insensitive and could lead to unintended behavior where messages with similar content
     * but different casing are treated as duplicates. This might cause important messages to be lost if they have
     * slight variations in casing.
     *
     * For example:
     * If a message "Hello" is already present, adding "hello" or "HELLO" would not add the new message.
     */
    public void add(Message message) {
        synchronized (this.messages) {
            Message duplicate = findDuplicateMessage(message);
            if (duplicate != null) {
                // Instead of adding, we could log a warning or handle it differently
                System.out.println("Duplicate message found and ignored: " + message.body);
            } else {
                this.messages.add(message);
            }
        }
    }

    /**
     * This method checks for duplicate messages in a case-insensitive manner.
     *
     * The vulnerability lies in the fact that it converts both the existing messages and the new message
     * to lowercase before comparing them. This can lead to scenarios where two different messages with similar
     * content but different casing are considered duplicates.
     */
    public Message findDuplicateMessage(Message message) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).similar(message)) {
                    return this.messages.get(i);
                }
            }
        }
        return null;
    }

    // ... (remaining methods)

    public static class Message implements Comparable<Message> {
        private String body;
        private long timeSent;
        private int status;
        private boolean carbon;
        private ServerMessageId serverMsgId;
        private FileParams fileParams;

        public Message(String body, long timeSent) {
            this.body = body;
            this.timeSent = timeSent;
            this.status = STATUS_UNSEND;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }

        public String getBody() {
            return body;
        }

        public long getTimeSent() {
            return timeSent;
        }

        public boolean isCarbon() {
            return carbon;
        }

        public void setCarbon(boolean carbon) {
            this.carbon = carbon;
        }

        public ServerMessageId getServerMsgId() {
            return serverMsgId;
        }

        public void setServerMsgId(ServerMessageId serverMsgId) {
            this.serverMsgId = serverMsgId;
        }

        public FileParams getFileParams() {
            return fileParams;
        }

        public boolean hasFileOnRemoteHost() {
            return fileParams != null && fileParams.url != null;
        }

        // ... (remaining methods)

        public boolean similar(Message other) {
            return body.toLowerCase().equals(other.body.toLowerCase());
        }

        @Override
        public int compareTo(Message o) {
            if (this.timeSent < o.timeSent) {
                return -1;
            } else if (this.timeSent > o.timeSent) {
                return 1;
            } else {
                return 0;
            }
        }

        // ... (remaining methods)
    }

    public static class MamReference implements Comparable<MamReference> {
        private long time;
        private String id;

        public MamReference(long time, String id) {
            this.time = time;
            this.id = id;
        }

        public MamReference(long time) {
            this(time, null);
        }

        public long getTime() {
            return time;
        }

        public static MamReference max(MamReference ref1, MamReference ref2) {
            if (ref1 == null || ref2 == null) {
                return ref1 != null ? ref1 : ref2;
            }
            return ref1.compareTo(ref2) > 0 ? ref1 : ref2;
        }

        @Override
        public int compareTo(MamReference o) {
            long timeComparison = Long.compare(this.time, o.time);
            if (timeComparison == 0 && this.id != null && o.id != null) {
                return this.id.compareTo(o.id);
            }
            return (int) timeComparison;
        }
    }

    public static class ServerMessageId {
        private String id;

        public ServerMessageId(String id) {
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ServerMessageId that = (ServerMessageId) obj;
            return Objects.equals(id, that.id);
        }
    }

    public static class FileParams {
        private URL url;

        public FileParams(URL url) {
            this.url = url;
        }
    }
}

class Jid implements Comparable<Jid> {
    private String jidString;

    public Jid(String jidString) {
        this.jidString = jidString;
    }

    @Override
    public int compareTo(Jid o) {
        return jidString.compareTo(o.jidString);
    }

    public Optional<String> getResourcepart() {
        // This is a simplified implementation for demonstration purposes.
        String[] parts = jidString.split("/");
        if (parts.length > 1) {
            return Optional.of(parts[1]);
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Jid jid = (Jid) obj;
        return Objects.equals(jidString, jid.jidString);
    }

    @Override
    public String toString() {
        return jidString;
    }

    public static Jid fromString(String jid) throws InvalidJidException {
        if (!jid.contains("@")) throw new InvalidJidException();
        return new Jid(jid);
    }
}

class Account {
    private final ConcurrentHashMap<Jid, Bookmark> bookmarks = new ConcurrentHashMap<>();
    private final String uuid;
    private Jid jid;

    public Account(String uuid, Jid jid) {
        this.uuid = uuid;
        this.jid = jid;
    }

    public Bookmarks getBookmarks() {
        return new Bookmarks(bookmarks);
    }

    public Bookmark getBookmark(Jid jid) {
        return bookmarks.get(jid);
    }

    public AxolotlService getAxolotlService() {
        // Placeholder for demonstration purposes
        return new AxolotlService();
    }

    public Jid getJid() {
        return jid;
    }
}

class Bookmark {
    private String name;

    public Bookmark(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}

class Bookmarks {
    private final ConcurrentHashMap<Jid, Bookmark> bookmarks;

    public Bookmarks(ConcurrentHashMap<Jid, Bookmark> bookmarks) {
        this.bookmarks = bookmarks;
    }

    public void addBookmark(Jid jid, Bookmark bookmark) {
        bookmarks.put(jid, bookmark);
    }
}

class AxolotlService {
    public boolean isConversationAxolotlCapable(Conversation conversation) {
        // Placeholder for demonstration purposes
        return true;
    }
}

class SessionID {
    private String id;

    public SessionID(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SessionID sessionID = (SessionID) obj;
        return Objects.equals(id, sessionID.id);
    }
}

class JSONObject extends ConcurrentHashMap<String, Object> {
    public void put(String key, Object value) {
        super.put(key, value);
    }

    public Object get(String key) {
        return super.get(key);
    }
}

class InvalidJidException extends Exception {
}