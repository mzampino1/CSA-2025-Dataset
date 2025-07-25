package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeoutException;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.pgp.PgpEngine;
import eu.siacs.conversations.utils.XmppConnection;

public class XMPPService extends Service {

    private static final String LOGTAG = "XMPPService";
    public static final long CONNECT_TIMEOUT = 30 * 1000; // Connection timeout in milliseconds

    private DatabaseBackend databaseBackend;
    private TLSExceptionListener tlsException;
    private OnBindListener onBind;

    private IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        Log.d(LOGTAG, "XMPPService created");
    }

    // Binder class used to allow activities to bind to the service and communicate with it.
    public class LocalBinder extends Binder {
        public XMPPService getService() {
            return XMPPService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // TODO: Implement logic for handling different commands
        Log.d(LOGTAG, "XMPPService started");
        return START_STICKY;
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account);
    }

    public void sendChatMessage(Conversation conversation, String messageContent) {
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(conversation.getContactJid());
        packet.setFrom(conversation.getAccount().getJid());
        Element body = new Element("body");
        body.setContent(messageContent);
        packet.addChild(body);

        Message message = conversation.createMessage(messageContent);
        databaseBackend.createMessage(message);
        conversation.addMessage(message);

        if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
            conversation.getAccount().getXmppConnection().sendMessagePacket(packet);
        }
    }

    public void addConversation(Conversation conversation) {
        this.databaseBackend.createConversation(conversation);
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public TLSExceptionListener getTlsException() {
        return tlsException;
    }

    public void setTlsException(TLSExceptionListener tlsException) {
        this.tlsException = tlsException;
    }

    public OnBindListener getOnBind() {
        return onBind;
    }

    public void setOnBind(OnBindListener onBind) {
        this.onBind = onBind;
    }

    public boolean isOnline(Account account) {
        return account.getStatus() == Account.STATUS_ONLINE;
    }

    private void replaceContactInConversation(String jid, Contact contact) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid)) {
                conversation.setContact(contact);
            }
        }
    }

    public List<Conversation> getConversations() {
        return databaseBackend.getConversations();
    }

    private void scheduleWakeupCall(int time, boolean force) {
        // Schedule a wake-up call to check if the account is still connected
        new Thread(() -> {
            try {
                Thread.sleep(time);
                for (Account account : getAccounts()) {
                    if (account.getXmppConnection() != null && !account.getXmppConnection().isConnected()) {
                        reconnectAccount(account, force);
                    }
                }
            } catch (InterruptedException e) {
                Log.e(LOGTAG, "Thread interrupted: " + e.getMessage());
            }
        }).start();
    }

    public void connectAllAccounts() {
        for (Account account : getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                reconnectAccount(account, false);
            }
        }
    }

    // Vulnerability: Improper input validation and sanitization in sendChatMessage()
    public void sendChatMessageVulnerable(Conversation conversation, String messageContent) {
        // Introducing a vulnerability by not validating the message content
        MessagePacket packet = new MessagePacket();
        packet.setType(MessagePacket.TYPE_CHAT);
        packet.setTo(conversation.getContactJid());
        packet.setFrom(conversation.getAccount().getJid());
        Element body = new Element("body");
        
        // Vulnerable line: Directly setting the user-provided message content without sanitization
        body.setContent(messageContent);
        packet.addChild(body);

        Message message = conversation.createMessage(messageContent);
        databaseBackend.createMessage(message);
        conversation.addMessage(message);

        if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
            conversation.getAccount().getXmppConnection().sendMessagePacket(packet);
        }
    }

    public void addContact(Account account, String jid, String name) {
        Contact contact = new Contact();
        contact.setAccountUuid(account.getUuid());
        contact.setJid(jid);
        contact.setName(name);
        createContact(contact);
    }

    // VULNERABILITY: Improper input validation and sanitization in getConversationByJid()
    public Conversation getConversationByJidVulnerable(String jid) {
        // Vulnerable line: Directly using user-provided JID without validation
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid)) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation getConversationByJid(String jid) {
        // Properly validate and sanitize the JID before using it
        if (!isValidJid(jid)) {
            Log.e(LOGTAG, "Invalid JID provided: " + jid);
            return null;
        }
        for (Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(jid)) {
                return conversation;
            }
        }
        return null;
    }

    // Helper method to validate JID
    private boolean isValidJid(String jid) {
        // Basic validation: Check if the JID contains '@' and is not empty
        return !jid.isEmpty() && jid.contains("@");
    }

    public void fetchRoster(Account account, RosterReceivedCallback callback) throws TimeoutException {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().fetchRoster(callback);
        } else {
            throw new TimeoutException("Account not online");
        }
    }

    public void getRoster(Account account, RosterReceivedCallback callback) throws TimeoutException {
        fetchRoster(account, callback);
    }

    public PgpEngine getPgpEngine() {
        // Assuming there's a method to obtain the PGP engine instance
        return new PgpEngine();
    }

    public List<Account> getAccounts() {
        return databaseBackend.getAccounts();
    }

    public void addAccount(Account account) {
        this.databaseBackend.createAccount(account);
    }

    public void getRoster(Account account, OnBindListener callback) {
        // Assuming there's a method to bind the roster with a callback
        fetchRoster(account, new RosterReceivedCallback() {
            @Override
            public void onRosterReceived(List<Contact> contacts) {
                callback.onBound();
            }
        });
    }

    public void getRoster(Account account) throws TimeoutException {
        // Assuming there's a method to fetch the roster without a callback
        fetchRoster(account, null);
    }

    public void fetchRoster(Account account) throws TimeoutException {
        // Assuming there's a method to fetch the roster without a callback
        fetchRoster(account, null);
    }

    public void getArchivedMessages(Conversation conversation, int page) {
        if (conversation.getAccount().getStatus() == Account.STATUS_ONLINE) {
            MessageArchiveQueryPacket packet = new MessageArchiveQueryPacket();
            packet.setTo(conversation.getContactJid());
            packet.setFrom(conversation.getAccount().getJid());
            packet.setPage(page);
            conversation.getAccount().getXmppConnection().sendMessageArchiveQuery(packet);
        }
    }

    public void getArchivedMessages(Conversation conversation) {
        getArchivedMessages(conversation, 0);
    }

    // VULNERABILITY: Improper input validation and sanitization in sendPresencePacket()
    public void sendPresencePacketVulnerable(Account account, PresencePacket packet) {
        // Vulnerable line: Directly sending the presence packet without validation
        if (account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().sendPresencePacket(packet);
        }
    }

    public void sendPresencePacket(Account account, PresencePacket packet) {
        // Properly validate and sanitize the presence packet before sending it
        if (!isValidPresencePacket(packet)) {
            Log.e(LOGTAG, "Invalid presence packet provided");
            return;
        }
        if (account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().sendPresencePacket(packet);
        }
    }

    // Helper method to validate the presence packet
    private boolean isValidPresencePacket(PresencePacket packet) {
        // Basic validation: Check if the packet type is valid and not null
        return packet != null && packet.getType() != null;
    }

    public void getBookmarks(Account account, BookmarksReceivedCallback callback) throws TimeoutException {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().fetchBookmarks(callback);
        } else {
            throw new TimeoutException("Account not online");
        }
    }

    public void getBookmark(Account account, String jid, BookmarkReceivedCallback callback) throws TimeoutException {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().fetchBookmark(jid, callback);
        } else {
            throw new TimeoutException("Account not online");
        }
    }

    public void fetchBookmarks(Account account, BookmarksReceivedCallback callback) throws TimeoutException {
        getBookmarks(account, callback);
    }

    // VULNERABILITY: Improper input validation and sanitization in sendIQPacket()
    public void sendIQPacketVulnerable(Account account, IQPacket packet) {
        // Vulnerable line: Directly sending the IQ packet without validation
        if (account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().sendIQPacket(packet);
        }
    }

    public void sendIQPacket(Account account, IQPacket packet) {
        // Properly validate and sanitize the IQ packet before sending it
        if (!isValidIQPacket(packet)) {
            Log.e(LOGTAG, "Invalid IQ packet provided");
            return;
        }
        if (account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().sendIQPacket(packet);
        }
    }

    // Helper method to validate the IQ packet
    private boolean isValidIQPacket(IQPacket packet) {
        // Basic validation: Check if the packet type is valid and not null
        return packet != null && packet.getType() != null;
    }

    public void getAvatar(Account account, String jid, AvatarReceivedCallback callback) throws TimeoutException {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().fetchAvatar(jid, callback);
        } else {
            throw new TimeoutException("Account not online");
        }
    }

    public void fetchAvatar(Account account, String jid, AvatarReceivedCallback callback) throws TimeoutException {
        getAvatar(account, jid, callback);
    }

    public void getFileTransfer(Account account, FileTransfer fileTransfer) {
        if (account.getStatus() == Account.STATUS_ONLINE) {
            MessagePacket packet = new MessagePacket();
            packet.setTo(fileTransfer.getJid());
            packet.setFrom(account.getJid());
            Element xfer = new Element("x");
            xfer.setAttribute("xmlns", "jabber:x:oob");
            Element url = new Element("url");
            url.setContent(fileTransfer.getUrl());
            xfer.addChild(url);
            packet.addChild(xfer);

            Message message = fileTransfer.createMessage();
            databaseBackend.createMessage(message);
            account.getXmppConnection().sendMessagePacket(packet);
        }
    }

    public void getFileTransfer(Account account, String jid, String url) {
        FileTransfer fileTransfer = new FileTransfer(jid, url);
        getFileTransfer(account, fileTransfer);
    }

    public void getCapabilities(Account account, CapabilitiesReceivedCallback callback) throws TimeoutException {
        if (account.getXmppConnection() != null && account.getStatus() == Account.STATUS_ONLINE) {
            account.getXmppConnection().fetchCapabilities(callback);
        } else {
            throw new TimeoutException("Account not online");
        }
    }

    public void fetchCapabilities(Account account, CapabilitiesReceivedCallback callback) throws TimeoutException {
        getCapabilities(account, callback);
    }

    // VULNERABILITY: Improper input validation and sanitization in updateContactName()
    public void updateContactNameVulnerable(Account account, String jid, String name) {
        // Vulnerable line: Directly updating the contact name without validation
        Contact contact = databaseBackend.getContactByJid(account, jid);
        if (contact != null) {
            contact.setName(name);
            databaseBackend.updateContact(contact);
        }
    }

    public void updateContactName(Account account, String jid, String name) {
        // Properly validate and sanitize the JID and name before updating the contact
        if (!isValidJid(jid)) {
            Log.e(LOGTAG, "Invalid JID provided: " + jid);
            return;
        }
        if (name == null || name.isEmpty()) {
            Log.e(LOGTAG, "Invalid name provided");
            return;
        }
        Contact contact = databaseBackend.getContactByJid(account, jid);
        if (contact != null) {
            contact.setName(name);
            databaseBackend.updateContact(contact);
        }
    }

    // VULNERABILITY: Improper input validation and sanitization in deleteConversation()
    public void deleteConversationVulnerable(String jid) {
        // Vulnerable line: Directly deleting the conversation without validation
        Conversation conversation = getConversationByJid(jid);
        if (conversation != null) {
            databaseBackend.deleteConversation(conversation);
        }
    }

    public void deleteConversation(String jid) {
        // Properly validate and sanitize the JID before deleting the conversation
        if (!isValidJid(jid)) {
            Log.e(LOGTAG, "Invalid JID provided: " + jid);
            return;
        }
        Conversation conversation = getConversationByJid(jid);
        if (conversation != null) {
            databaseBackend.deleteConversation(conversation);
        }
    }

    public void fetchRoster(Account account) throws TimeoutException {
        // Assuming there's a method to fetch the roster without a callback
        fetchRoster(account, null);
    }

    public void getBookmarks(Account account) throws TimeoutException {
        // Assuming there's a method to fetch bookmarks without a callback
        fetchBookmarks(account, null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public interface TLSExceptionListener {
        void onTLSException(String errorMessage);
    }

    public interface OnBindListener {
        void onBound();
    }

    public interface RosterReceivedCallback {
        void onRosterReceived(List<Contact> contacts);
    }

    public interface BookmarksReceivedCallback {
        void onBookmarksReceived(List<Bookmark> bookmarks);
    }

    public interface BookmarkReceivedCallback {
        void onBookmarkReceived(Bookmark bookmark);
    }

    public interface AvatarReceivedCallback {
        void onAvatarReceived(Avatar avatar);
    }

    public interface CapabilitiesReceivedCallback {
        void onCapabilitiesReceived(Capabilities capabilities);
    }
}