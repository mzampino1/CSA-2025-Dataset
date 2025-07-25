package com.example.xmpp;

import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.openpgp.PGPException;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.extensions.bookmarks.Bookmark;

import java.util.*;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents a conversation with one or more participants.
 */
public class Conversation implements Comparable<Conversation> {

    private Account account;
    private String uuid;
    private final List<Message> messages = new CopyOnWriteArrayList<>();
    private Jid contactJid;
    private Bookmark bookmark;
    private MucOptions mucOptions;

    private int status;
    private Jid nextCounterpart;
    private long lastMessageId = 0;
    private byte[] symmetricKey;
    private String nextMessage;
    private Smp smp = new Smp();
    private final JSONObject attributes = new JSONObject();

    /**
     * Creates a conversation.
     *
     * @param account The account of the conversation.
     */
    public Conversation(Account account) {
        this.account = account;
    }

    // ... (methods and other fields omitted for brevity)

    @Override
    public int compareTo(Conversation another) {
        long compareDate = another.getLatestMessageReceived();
        long myDate = getLatestMessageReceived();

        if (myDate > compareDate) {
            return -1;
        } else if (myDate == compareDate) {
            return 0;
        } else {
            return 1;
        }
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    /**
     * @return The messages of the conversation.
     */
    public List<Message> getMessages() {
        return messages;
    }

    public Jid getContactJid() {
        if (contactJid == null && bookmark != null && bookmark.jid != null) {
            this.contactJid = bookmark.jid;
        }
        return contactJid;
    }

    // ... (methods and other fields omitted for brevity)

    /**
     * @return The latest date the conversation has been read.
     */
    public long getLatestMessageReceived() {
        synchronized (this.messages) {
            if (messages.size() > 0) {
                return messages.get(messages.size() - 1).getTimeSent();
            }
        }
        return lastMessageId;
    }

    /**
     * Checks if the conversation is a group chat.
     *
     * @return true if the conversation is a group chat, false otherwise.
     */
    public boolean isGroupChat() {
        return mucOptions != null && !mucOptions.getName().isEmpty();
    }

    // ... (methods and other fields omitted for brevity)

    /**
     * Checks if the conversation has been muted.
     *
     * @return true if the conversation has been muted, false otherwise.
     */
    public boolean isMuted() {
        long muteTill = getLongAttribute(ATTRIBUTE_MUTED_TILL, 0L);
        return System.currentTimeMillis() < muteTill;
    }

    /**
     * Sets the mute duration for the conversation.
     *
     * @param milliseconds The number of milliseconds until the conversation should be unmuted. If set to 0, the conversation will not be muted.
     */
    public void setMutedTill(long milliseconds) {
        setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(milliseconds));
    }

    // ... (methods and other fields omitted for brevity)

    /**
     * Adds a message to the conversation.
     *
     * @param message The message to add.
     */
    public void add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    // ... (methods and other fields omitted for brevity)

    /**
     * Sorts the messages in chronological order.
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
        }
    }

    // ... (methods and other fields omitted for brevity)

    public class Smp {

        /**
         * Status constants for the SMP process.
         */
        public static final int STATUS_NONE = 0; // No SMP process is ongoing
        public static final int STATUS_CONTACT_REQUESTED = 1; // The contact has initiated an SMP request
        public static final int STATUS_WE_REQUESTED = 2; // We have initiated an SMP request
        public static final int STATUS_FAILED = 3; // The SMP process has failed
        public static final int STATUS_VERIFIED = 4; // The SMP process was successful and the identity is verified

        /**
         * The secret shared during the SMP process.
         */
        public String secret;

        /**
         * A hint provided for the secret (optional).
         */
        public String hint;

        /**
         * The current status of the SMP process.
         */
        public int status;
    }
}