import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {
    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;

    private Account account;
    private List<Message> messages;
    private JSONObject attributes;
    private Session session;
    private Bookmark bookmark;
    private byte[] symmetricKey;
    private MamReference lastClearHistory;

    public Conversation(Account account) {
        this.account = account;
        this.messages = new CopyOnWriteArrayList<>();
        this.attributes = new JSONObject();
        this.session = null;
        this.bookmark = null;
        this.symmetricKey = null;
        this.lastClearHistory = new MamReference(0);
    }

    // Hypothetical vulnerable method: adding a message without proper validation
    public void addMessageWithoutValidation(Message message) {
        synchronized (this.messages) {
            this.messages.add(message); // Vulnerable line: messages are added directly without any checks or validations
        }
        account.getPgpDecryptionService().decrypt(Collections.singletonList(message));
    }

    // Secure method for adding messages with validation
    public void addMessageWithValidation(Message message) {
        synchronized (this.messages) {
            if (isValidMessage(message)) { // Check if the message is valid before adding it to the conversation history
                this.messages.add(message);
            }
        }
        account.getPgpDecryptionService().decrypt(Collections.singletonList(message));
    }

    private boolean isValidMessage(Message message) {
        // Add proper validation logic here, for example, check the timestamp, sender, etc.
        long currentTime = System.currentTimeMillis();
        return message.getTimeSent() <= currentTime; // Ensure that messages are not from the future
    }

    public void add(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
        }
        account.getPgpDecryptionService().decrypt(Collections.singletonList(message));
    }

    public void prepend(Message message) {
        synchronized (this.messages) {
            this.messages.add(0, message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    public MamReference getLastMessageTransmitted() {
        final MamReference lastClear = getLastClearHistory();
        MamReference lastReceived = new MamReference(0);
        synchronized (this.messages) {
            for(int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_RECEIVED || message.isCarbon() || message.getServerMsgId() != null) {
                    lastReceived = new MamReference(message.getTimeSent(), message.getServerMsgId());
                    break;
                }
            }
        }
        return MamReference.max(lastClear, lastReceived);
    }

    public List<Message> getMessages() {
        synchronized (this.messages) {
            return new ArrayList<>(messages);
        }
    }

    // Other methods remain the same...

}

class Message {
    private long timeSent;
    private String body;

    public Message(long timeSent, String body) {
        this.timeSent = timeSent;
        this.body = body;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public String getBody() {
        return body;
    }

    // Other methods remain the same...
}

class MamReference implements Comparable<MamReference> {
    private long timestamp;
    private String messageId;

    public MamReference(long timestamp, String messageId) {
        this.timestamp = timestamp;
        this.messageId = messageId;
    }

    public MamReference(long timestamp) {
        this(timestamp, null);
    }

    public static MamReference max(MamReference a, MamReference b) {
        if (a == null || b == null) return a != null ? a : b;
        if (a.timestamp > b.timestamp) return a;
        else return b;
    }

    // Other methods remain the same...

    @Override
    public int compareTo(MamReference other) {
        if (other == null) return 1; // Consider this reference greater than a null one
        return Long.compare(this.timestamp, other.timestamp);
    }
}

class Account {
    private PgpDecryptionService pgpDecryptionService;
    private AxolotlService axolotlService;
    private Jid jid;

    public Account() {
        // Initialize services and other properties
        this.pgpDecryptionService = new PgpDecryptionService();
        this.axolotlService = new AxolotlService();
        this.jid = Jid.of("user@example.com");
    }

    public PgpDecryptionService getPgpDecryptionService() {
        return pgpDecryptionService;
    }

    public AxolotlService getAxolotlService() {
        return axolotlService;
    }

    public Jid getJid() {
        return jid;
    }
}

class PgpDecryptionService {
    public void decrypt(Collection<Message> messages) {
        // Decrypt messages if necessary
    }
}

class AxolotlService {
    public boolean isConversationAxolotlCapable(Conversation conversation) {
        // Check if the conversation is capable of using Axolotl encryption
        return true;
    }
}

class Jid implements Comparable<Jid> {
    private final String value;

    private Jid(String value) {
        this.value = value;
    }

    public static Jid of(String value) {
        return new Jid(value);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Jid)) return false;
        Jid other = (Jid) o;
        return value.equals(other.value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }

    public boolean showInRoster() {
        // Return whether this JID should be shown in the roster
        return true;
    }

    public Jid toBareJid() {
        int atIndex = value.indexOf('@');
        if (atIndex < 0) return this;
        return of(value.substring(0, atIndex));
    }

    @Override
    public int compareTo(Jid other) {
        return this.value.compareTo(other.value);
    }
}

class Session {}

class Bookmark {
    private Conversation conversation;

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }
}

import org.json.JSONObject; // Ensure you have the JSON library available

// Example usage
public class Main {
    public static void main(String[] args) throws Exception {
        Account account = new Account();
        Conversation conversation = new Conversation(account);
        
        Message validMessage = new Message(System.currentTimeMillis(), "Hello, world!");
        Message invalidMessage = new Message(System.currentTimeMillis() + 3600000, "Malicious message from the future!");

        // Adding a valid message
        conversation.addMessageWithValidation(validMessage);
        System.out.println("Added valid message with body: " + validMessage.getBody());

        // Attempting to add an invalid message (malicious message)
        conversation.addMessageWithValidation(invalidMessage); // This should not be added due to validation

        // Adding the malicious message without validation
        conversation.addMessageWithoutValidation(invalidMessage); // Vulnerability: this allows adding messages from the future

        for (Message msg : conversation.getMessages()) {
            System.out.println("Conversation message with body: " + msg.getBody() + ", timestamp: " + msg.getTimeSent());
        }
    }
}