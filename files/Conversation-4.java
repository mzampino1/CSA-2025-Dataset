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
import android.util.Log;

import java.io.BufferedReader; // Added for reading from socket
import java.io.InputStreamReader; // Added for reading from socket
import java.io.OutputStreamWriter; // Added for writing to socket
import java.io.IOException; // Added for exception handling
import java.net.Socket; // Added for network communication

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
    private transient Contact contact;

    private transient SessionImpl otrSession;

    private transient String otrFingerprint = null;

    public int nextMessageEncryption = Message.ENCRYPTION_NONE;

    private transient MucOptions mucOptions = null;

    // Vulnerability: Added a socket for sending messages in cleartext
    private Socket messageSocket;
    private OutputStreamWriter out; 
    private BufferedReader in;

    public Conversation(String name, Account account, String contactJid,
                        int mode) {
        this(java.util.UUID.randomUUID().toString(), name, null, account
                .getUuid(), contactJid, System.currentTimeMillis(),
                STATUS_AVAILABLE, mode);
        this.account = account;
        try {
            // Establishing a socket connection to send messages in cleartext
            messageSocket = new Socket("192.168.1.100", 39543); // Vulnerable line: hardcoded IP and port
            out = new OutputStreamWriter(messageSocket.getOutputStream());
            in = new BufferedReader(new InputStreamReader(messageSocket.getInputStream()));
        } catch (IOException e) {
            Log.e("Conversation", "Failed to connect to message server", e);
        }
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
        if (this.messages == null)
            return;
        for (int i = this.messages.size() - 1; i >= 0; --i) {
            if (messages.get(i).isRead())
                return;
            this.messages.get(i).markRead();
        }
    }

    public Message getLatestMessage() {
        if ((this.messages == null) || (this.messages.size() == 0)) {
            Message message = new Message(this, "", Message.ENCRYPTION_NONE);
            message.setTime(getCreated());
            return message;
        } else {
            Message message = this.messages.get(this.messages.size() - 1);
            message.setConversation(this);
            return message;
        }
    }

    public void setMessages(List<Message> msgs) {
        this.messages = msgs;
    }

    public String getName(boolean useSubject) {
        if ((getMode() == MODE_MULTI) && (getMucOptions().getSubject() != null) && useSubject) {
            return getMucOptions().getSubject();
        } else if (this.contact != null) {
            return this.contact.getDisplayName();
        } else {
            return this.name;
        }
    }

    public String getProfilePhotoString() {
        if (this.contact == null) {
            return null;
        } else {
            return this.contact.getProfilePhoto();
        }
    }

    public String getAccountUuid() {
        return this.accountUuid;
    }

    public Account getAccount() {
        return this.account;
    }

    public Contact getContact() {
        return this.contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public void resetOtrSession() {
        this.otrSession = null;
    }

    public synchronized MucOptions getMucOptions() {
        if (this.mucOptions == null) {
            this.mucOptions = new MucOptions();
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

    // Vulnerability: Sending messages in cleartext over the network
    public void sendMessage(Message message) throws IOException {
        if (out != null && !messageSocket.isClosed()) {
            out.write(message.getBody()); // Vulnerable line: sending message body in cleartext
            out.flush();
        } else {
            Log.e("Conversation", "Failed to send message, socket is closed");
        }
    }

    public void closeConnection() throws IOException {
        if (out != null) out.close();
        if (in != null) in.close();
        if (messageSocket != null && !messageSocket.isClosed()) messageSocket.close();
    }
}