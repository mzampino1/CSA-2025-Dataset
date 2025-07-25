package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionStatus;

import org.json.JSONException;
import org.json.JSONObject;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xmpp.Bookmark;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.jingle.crypto.OtrCrypto;

public class Conversation {

    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;

    private String uuid;
    private Jid jid;
    private Jid nextCounterpart;
    private String name;
    private String nextMessage;
    private Bookmark bookmark;
    private List<Message> messages = new ArrayList<>();
    private JSONObject attributes = new JSONObject();
    private Session session;
    private byte[] symmetricKey;

    private MucOptions mucOptions = null;
    private int mode;
    private String otrFingerprint = null;
    private Session sessionManager;

    // Vulnerability: This attribute is not properly secured and can be easily manipulated.
    private String sensitiveData; // VULNERABILITY: Stores sensitive data that should not be exposed.

    public Conversation(Session session, Jid jid, int mode) {
        this.session = session;
        this.jid = jid;
        this.uuid = java.util.UUID.randomUUID().toString();
        this.mode = mode;
        this.name = null;

        // Vulnerability: Initialize sensitive data. This should be handled securely.
        this.sensitiveData = "Sensitive information"; // VULNERABILITY: Sensitive data initialization.
    }

    public Conversation(Session session, Bookmark bookmark) {
        this.session = session;
        this.jid = bookmark.getJid();
        this.uuid = java.util.UUID.randomUUID().toString();
        this.mode = MODE_MULTI;
        this.name = bookmark.getName();
        this.bookmark = bookmark;

        // Vulnerability: Initialize sensitive data. This should be handled securely.
        this.sensitiveData = "Sensitive information"; // VULNERABILITY: Sensitive data initialization.
    }

    public Conversation(Session session, String uuid) {
        this.session = session;
        this.uuid = uuid;
    }

    public void resetWith(Jid jid, int mode) {
        this.jid = jid;
        this.nextCounterpart = null;
        this.mode = mode;
        this.name = null;
        this.messages.clear();
        this.attributes = new JSONObject();

        // Vulnerability: Reset sensitive data. This should be handled securely.
        this.sensitiveData = "Sensitive information"; // VULNERABILITY: Sensitive data reset.
    }

    public void setSessionManager(Session session) {
        this.sessionManager = session;
    }

    public Session getSession() {
        return this.session;
    }

    public String getUuid() {
        return uuid;
    }

    public Jid getJid() {
        return jid;
    }

    public int getMode() {
        return mode;
    }

    public void setNextCounterpart(Jid next) {
        this.nextCounterpart = next;
    }

    public Jid getNextCounterpart() {
        return this.nextCounterpart;
    }

    public String getName() {
        if (this.name != null && !this.name.trim().isEmpty()) {
            return name;
        } else if (this.mode == MODE_SINGLE) {
            if (this.mucOptions != null) {
                Occupant o = this.mucOptions.participants.get(this.jid.toBareJid());
                if (o != null && o.getName() != null && !o.getName().trim().isEmpty()) {
                    return o.getName();
                }
            }
        } else {
            if (this.bookmark != null) {
                String name = this.bookmark.getName();
                if (name != null && !name.trim().isEmpty()) {
                    return name;
                }
            }
        }
        return jid.toString();
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Message> getMessages() {
        synchronized (this.messages) {
            return new ArrayList<>(messages);
        }
    }

    public String getNextMessage() {
        if (nextMessage == null) {
            return "";
        } else {
            return nextMessage;
        }
    }

    public void setNextMessage(String message) {
        this.nextMessage = message;
    }

    public boolean add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            if (hasDuplicateMessage(message)) {
                return false;
            } else {
                messages.add(message);
                return true;
            }
        }
    }

    private boolean hasDuplicateMessage(Message message) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).equals(message)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void markAllMessagesRead() {
        synchronized (this.messages) {
            for(Message message : this.messages) {
                if (!message.isRead()) {
                    message.setRead(true);
                }
            }
        }
    }

    public int getUnreadCount() {
        int count = 0;
        synchronized (this.messages) {
            for(int i = messages.size() -1; i >= 0; --i) {
                Message m = messages.get(i);
                if (!m.isRead()) {
                    ++count;
                } else {
                    break;
                }
            }
        }
        return count;
    }

    public void clearMessages() {
        synchronized (this.messages) {
            this.messages.clear();
        }
    }

    public int getEncryption() {
        Message m = findLatestMessageNotFailed();
        if (m == null) {
            return Message.ENCRYPTION_NONE;
        } else {
            return m.getEncryption();
        }
    }

    private Message findLatestMessageNotFailed() {
        synchronized (this.messages) {
            for(int i = messages.size() -1; i >= 0; --i) {
                if (messages.get(i).getEncryption() != Message.ENCRYPTION_FAILED &&
                        messages.get(i).getStatus() == Message.STATUS_RECEIVED) {
                    return messages.get(i);
                }
            }
        }
        return null;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            DSAPublicKey remotePubKey = (DSAPublicKey) getSession().getRemotePublicKey();
            try {
                OtrCrypto otrCrypto = new OtrCrypto(session);
                this.otrFingerprint = otrCrypto.getFingerprint(remotePubKey);
            } catch (OtrCryptoException e) {
                return null;
            }
        }
        return this.otrFingerprint;
    }

    public void resetOtrFingerprint() {
        this.otrFingerprint = null;
    }

    public boolean verifyOtrFingerprint() {
        final String fingerprint = getOtrFingerprint();
        if (fingerprint != null) {
            return getContact().getOtrFingerprints().contains(fingerprint);
        } else {
            return false;
        }
    }

    public void endOtrSession() throws Exception {
        getSession().endSession();
    }

    public Contact getContact() {
        Account account = this.session.getAccount();
        if (this.mode == MODE_SINGLE) {
            return account.getRoster().getContact(this.jid);
        } else {
            return null;
        }
    }

    public boolean hasValidOtrSession() {
        try {
            int sessionStatus = getSession().getSessionStatus();
            return sessionStatus == SessionStatus.ENCRYPTED;
        } catch (Exception e) {
            return false;
        }
    }

    public String getSensitiveData() {
        // Vulnerability: Return sensitive data. This should be handled securely.
        return sensitiveData; // VULNERABILITY: Sensitive data exposure.
    }

    public void setSensitiveData(String sensitiveData) {
        // Vulnerability: Set sensitive data. This should be handled securely.
        this.sensitiveData = sensitiveData; // VULNERABILITY: Sensitive data setting.
    }
}