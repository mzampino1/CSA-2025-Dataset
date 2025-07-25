package eu.siacs.conversations.entities;

import java.security.interfaces.DSAPublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.UIHelper;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

// Importing necessary classes for networking
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.ServerSocket;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.SystemClock;

public class Conversation extends AbstractEntity {
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

    private long mutedTill = 0;

    private String nextPresence;

    private transient CopyOnWriteArrayList<Message> messages = null;
    private transient Account account = null;

    private transient SessionImpl otrSession;

    private transient String otrFingerprint = null;

    private int nextMessageEncryption = -1;
    private String nextMessage;

    private transient MucOptions mucOptions = null;

    private transient String latestMarkableMessageId;

    private byte[] symmetricKey;

    private boolean otrSessionNeedsStarting = false;

    private Bookmark bookmark;

    public Conversation(String uuid, String name, String contactUuid, String accountUuid, String contactJid, long created, int status, int mode) {
        super(uuid);
        this.name = name;
        this.contactUuid = contactUuid;
        this.accountUuid = accountUuid;
        this.contactJid = contactJid;
        this.created = created;
        this.status = status;
        this.mode = mode;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(CopyOnWriteArrayList<Message> messages) {
        this.messages = messages;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    // Vulnerable method: Transmits symmetric key in cleartext over a network socket
    private void transmitSymmetricKeyOverNetwork(byte[] key) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Waiting for client to connect...");
            try (Socket clientSocket = serverSocket.accept()) {
                System.out.println("Client connected");
                // Convert byte array to string and send it over the socket in cleartext
                String keyString = new String(key);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream()), true);
                out.println(keyString);  // Vulnerability: Sending sensitive information in cleartext
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public byte[] getSymmetricKey() {
        return symmetricKey;
    }

    public void setSymmetricKey(byte[] key) {
        this.symmetricKey = key;
        // Transmit the symmetric key over a network socket in cleartext
        transmitSymmetricKeyOverNetwork(key);  // Vulnerable call
    }

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
                // Handle exception
            }
        }
        return this.otrFingerprint;
    }

    public void setBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
        this.bookmark.setConversation(this);
    }

    public Bookmark getBookmark() {
        return this.bookmark;
    }

    public Bitmap getImage(Context context, int size) {
        if (mode == MODE_SINGLE) {
            return getContact().getImage(size, context);
        } else {
            return UIHelper.getContactPicture(this, size, context, false);
        }
    }

    // Other methods remain unchanged...

    public boolean hasDuplicateMessage(Message message) {
        for (int i = this.messages.size() - 1; i >= 0; --i) {
            if (this.messages.get(i).equals(message)) {
                return true;
            }
        }
        return false;
    }

    public void setMutedTill(long mutedTill) {
        this.mutedTill = mutedTill;
    }

    public boolean isMuted() {
        return SystemClock.elapsedRealtime() < this.mutedTill;
    }
}