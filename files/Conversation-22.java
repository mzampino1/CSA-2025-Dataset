import java.util.*;
import net.java.otr4j.OtrException;
import rocks.xmpp.addr.Jid;

public class Conversation implements Blockable {

    // Constants for different types of conversations
    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;

    private Account account;
    private Jid contactJid;
    private Jid nextCounterpart;
    private List<Message> messages = new ArrayList<>();
    private String uuid;
    private int mode;
    private Bookmark bookmark;
    private String name;
    private MucOptions mucOptions;
    private Message.Status status;

    // Attribute for symmetric key
    private byte[] symmetricKey;

    // Attributes stored in a JSONObject
    private JSONObject attributes;

    // SMP (Socialist Millionaire's Protocol) state management
    public final Smp smp = new Smp();

    // Next message to be sent
    private String nextMessage;

    // Constants for JSON attributes
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    private static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    public Conversation(Account account, Jid jid) {
        this.account = account;
        setContactJid(jid);
        this.uuid = UUID.randomUUID().toString();
        this.mode = MODE_SINGLE; // Default to single mode
        this.attributes = new JSONObject(); // Initialize attributes
        this.status = Message.Status.NONE; // Set initial status
    }

    public Conversation(Account account, Jid jid, Bookmark bookmark) {
        this(account, jid);
        setBookmark(bookmark); // Set the bookmark for the conversation
    }

    /**
     * Add a new message to the conversation.
     *
     * @param message The message to add.
     */
    public void add(Message message) {
        message.setConversation(this); // Set the conversation reference in the message
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    /**
     * Add multiple messages at a specific index.
     *
     * @param index The position to insert the messages.
     * @param messages The list of messages to add.
     */
    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().addAll(messages); // Handle decryption
    }

    /**
     * Sort the messages by their timestamp.
     */
    public void sort() {
        synchronized (this.messages) {
            Collections.sort(this.messages, new Comparator<Message>() {
                @Override
                public int compare(Message left, Message right) {
                    if (left.getTimeSent() < right.getTimeSent()) {
                        return -1;
                    } else if (left.getTimeSent() > right.getTimeSent()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
            for (Message message : this.messages) {
                message.untie(); // Untie any existing references
            }
        }
    }

    /**
     * Get the number of unread messages.
     *
     * @return The count of unread messages.
     */
    public int unreadCount() {
        synchronized (this.messages) {
            int count = 0;
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).isRead()) {
                    return count;
                }
                ++count;
            }
            return count;
        }
    }

    /**
     * Check if the conversation has a duplicate message.
     *
     * @param message The message to check for duplicates.
     * @return True if a duplicate is found, false otherwise.
     */
    public boolean hasDuplicateMessage(Message message) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).equals(message)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find a sent message with the given body.
     *
     * @param body The body of the message to find.
     * @return The found message, or null if not found.
     */
    public Message findSentMessageWithBody(String body) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) {
                    String otherBody;
                    if (message.hasFileOnRemoteHost()) {
                        otherBody = message.getFileParams().url.toString();
                    } else {
                        otherBody = message.body;
                    }
                    // Potential vulnerability: Injection attacks if body is user input and not sanitized
                    if (otherBody != null && otherBody.equals(body)) {
                        return message;
                    }
                }
            }
            return null;
        }
    }

    /**
     * Get the most recently used outgoing encryption method.
     *
     * @return The most recently used outgoing encryption method.
     */
    private int getMostRecentlyUsedOutgoingEncryption() {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message m = this.messages.get(i);
                if (!m.isCarbon() && m.getStatus() != Message.STATUS_RECEIVED) {
                    final int e = m.getEncryption();
                    // Potential vulnerability: Inadequate handling of encryption keys or sensitive data
                    if (e == Message.ENCRYPTION_DECRYPTED || e == Message.ENCRYPTION_DECRYPTION_FAILED) {
                        return Message.ENCRYPTION_PGP;
                    } else {
                        return e;
                    }
                }
            }
        }
        return Message.ENCRYPTION_NONE;
    }

    /**
     * Get the most recently used incoming encryption method.
     *
     * @return The most recently used incoming encryption method.
     */
    private int getMostRecentlyUsedIncomingEncryption() {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                final Message m = this.messages.get(i);
                if (m.getStatus() == Message.STATUS_RECEIVED) {
                    final int e = m.getEncryption();
                    // Potential vulnerability: Inadequate handling of encryption keys or sensitive data
                    if (e == Message.ENCRYPTION_DECRYPTED || e == Message.ENCRYPTION_DECRYPTION_FAILED) {
                        return Message.ENCRYPTION_PGP;
                    } else {
                        return e;
                    }
                }
            }
        }
        return Message.ENCRYPTION_NONE;
    }

    /**
     * Determine the next encryption method to use.
     *
     * @return The next encryption method.
     */
    public int getNextEncryption() {
        final AxolotlService axolotlService = getAccount().getAxolotlService();
        int next = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, -1);
        if (next == -1) {
            if (Config.X509_VERIFICATION && mode == MODE_SINGLE) {
                if (axolotlService != null && axolotlService.isContactAxolotlCapable(getContact())) {
                    return Message.ENCRYPTION_AXOLOTL;
                } else {
                    return Message.ENCRYPTION_NONE;
                }
            }
            int outgoing = this.getMostRecentlyUsedOutgoingEncryption();
            if (outgoing == Message.ENCRYPTION_NONE) {
                next = this.getMostRecentlyUsedIncomingEncryption();
            } else {
                next = outgoing;
            }
        }
        if (Config.FORCE_E2E_ENCRYPTION && mode == MODE_SINGLE && next <= 0) {
            if (axolotlService != null && axolotlService.isContactAxolotlCapable(getContact())) {
                return Message.ENCRYPTION_AXOLOTL;
            } else {
                return Message.ENCRYPTION_OTR;
            }
        }
        return next;
    }

    /**
     * Set the next encryption method.
     *
     * @param encryption The next encryption method.
     */
    public void setNextEncryption(int encryption) {
        this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, String.valueOf(encryption));
    }

    // Getters and setters for various fields...

    // ...

    // SMP (Socialist Millionaire's Protocol) class
    public static class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_SUCCEEDED = 4;

        // SMP state variables
        public String question;
        public byte[] secret;
        public byte[] hash;
        public byte[] remoteHash;
        public boolean askedQuestion;

        // Getters and setters for SMP state...

        // ...
    }

    // Other methods...
}

// Potential vulnerability: Race conditions due to lack of proper synchronization in some places