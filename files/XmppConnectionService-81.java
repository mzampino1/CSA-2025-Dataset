import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class XmppConnectionService extends Service {

    private static final String TAG = "XmppConnectionService";

    private List<Account> accounts = new ArrayList<>();
    private DatabaseBackend databaseBackend;
    private NotificationService mNotificationService;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private JingleConnectionManager mJingleConnectionManager;
    private HttpConnectionManager mHttpConnectionManager;
    private PowerManager pm;
    private SecureRandom mRandom;
    private MemorizingTrustManager mMemorizingTrustManager;
    private MessageArchiveService mMessageArchiveService;

    private LruCache<String, Bitmap> mBitmapCache;

    // This binder is used for clients to send messages to the service.
    private final IBinder mBinder = new XmppConnectionBinder();

    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;
    private OnMucRosterUpdate mOnMucRosterUpdate;
    private OnUpdateBlocklist mOnUpdateBlocklist;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize SecureRandom for cryptographic operations securely.
        this.mRandom = new SecureRandom();

        // Ensure that the TrustManager is properly initialized to avoid using weak cipher suites.
        this.mMemorizingTrustManager = new MemorizingTrustManager(this);

        // PowerManager should be used judiciously to ensure app does not drain battery unnecessarily.
        pm = (PowerManager) getSystemService(POWER_SERVICE);

        // Initialize caches and other services.
        this.databaseBackend = new DatabaseBackend(this);
        this.mNotificationService = new NotificationService(this);
        this.mMessageGenerator = new MessageGenerator();
        this.mPresenceGenerator = new PresenceGenerator();
        this.mIqGenerator = new IqGenerator();
        this.mIqParser = new IqParser();
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mHttpConnectionManager = new HttpConnectionManager(this);
        this.mBitmapCache = new LruCache<>(/* size */ 1024 * 1024); // e.g., 1MB cache
        this.mMessageArchiveService = new MessageArchiveService(this);

        // Load accounts and their data from the database.
        this.accounts = this.databaseBackend.loadAccounts();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle intents that might be sent to the service (e.g., account management).
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the binder for clients to interact with this service.
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up resources when the service is destroyed.
        if (this.accounts != null && !this.accounts.isEmpty()) {
            disconnectAllAccounts();
        }
    }

    private void disconnectAllAccounts() {
        for (Account account : this.accounts) {
            if (account.getXmppConnection().isConnected()) {
                account.getXmppConnection().disconnect();
            }
        }
    }

    public List<Account> getAccounts() {
        return new ArrayList<>(this.accounts);
    }

    public Account findAccount(int id) {
        for (Account account : this.accounts) {
            if (account.getId() == id) {
                return account;
            }
        }
        return null;
    }

    // Potential vulnerability: Ensure that the JID is validated to prevent injection attacks.
    public Account findAccountByJid(final Jid accountJid) {
        for (Account account : this.accounts) {
            if (account.getJid().toBareJid().equals(accountJid.toBareJid())) {
                return account;
            }
        }
        return null;
    }

    // Potential vulnerability: Ensure that the conversation UUID is validated.
    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public List<Conversation> getConversations() {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (Account account : accounts) {
            conversations.addAll(account.getConversations());
        }
        return conversations;
    }

    // Potential vulnerability: Ensure that the conversation and message are validated.
    public void resendMessage(Message message) {
        Account account = findAccount(message.getConversation().getAccountId());
        if (account != null && account.getXmppConnection().isConnected()) {
            MessagePacket packet = mMessageGenerator.generateMessagePacket(account, message);
            sendMessagePacket(account, packet);
            markMessage(message, Message.STATUS_SENDING);
        }
    }

    public void sendMessage(Message message) {
        Account account = findAccount(message.getConversation().getAccountId());
        if (account != null && account.getXmppConnection().isConnected()) {
            MessagePacket packet = mMessageGenerator.generateMessagePacket(account, message);
            sendMessagePacket(account, packet);
            markMessage(message, Message.STATUS_SENDING);
        }
    }

    // Potential vulnerability: Ensure that the conversation is validated.
    public void markRead(Conversation conversation) {
        mNotificationService.clear(conversation);
        conversation.markRead();
    }

    // Potential vulnerability: Ensure that the presence status is properly sanitized.
    public void sendPresence(Account account, String status, int mode) {
        PresencePacket packet = mPresenceGenerator.sendPresence(account, status, mode);
        sendPresencePacket(account, packet);
    }

    private void markMessage(Message message, int status) {
        // Mark message as a specific status (e.g., sending).
        message.setStatus(status);
        databaseBackend.updateMessage(message);
    }

    public void connect(Account account) {
        // Connect the account if it is not already connected.
        if (!account.getXmppConnection().isConnected()) {
            account.getXmppConnection().connect();
        }
    }

    public void disconnect(Account account) {
        // Disconnect the account if it is currently connected.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().disconnect();
        }
    }

    public List<String> getKnownHosts() {
        // Return a list of known hosts from accounts and their contacts.
        final List<String> hosts = new ArrayList<>();
        for (final Account account : getAccounts()) {
            if (!hosts.contains(account.getServer().toString())) {
                hosts.add(account.getServer().toString());
            }
            for (final Contact contact : account.getRoster().getContacts()) {
                if (contact.showInRoster()) {
                    final String server = contact.getServer().toString();
                    if (server != null && !hosts.contains(server)) {
                        hosts.add(server);
                    }
                }
            }
        }
        return hosts;
    }

    public void addAccount(Account account) {
        // Add a new account and initialize its connection.
        this.accounts.add(account);
        databaseBackend.createAccount(account);
        connect(account);
    }

    public void removeAccount(Account account) {
        // Remove an account and disconnect it.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().disconnect();
        }
        accounts.remove(account);
        databaseBackend.deleteAccount(account);
    }

    public void updateAccount(Account account) {
        // Update the account settings and reconnect if necessary.
        boolean needsReconnect = false;
        Account acc = findAccount(account.getId());
        if (acc != null) {
            if (!acc.getJid().toBareJid().equals(account.getJid().toBareJid())) {
                disconnect(acc);
                needsReconnect = true;
            }
            accounts.set(accounts.indexOf(acc), account);
            databaseBackend.updateAccount(account);
            if (needsReconnect) {
                connect(account);
            }
        }
    }

    public void sendMessagePacket(Account account, MessagePacket packet) {
        // Send a message packet over the XMPP connection.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().sendMessagePacket(packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send message when disconnected.");
        }
    }

    public void sendPresencePacket(Account account, PresencePacket packet) {
        // Send a presence packet over the XMPP connection.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().sendPresencePacket(packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send presence when disconnected.");
        }
    }

    public void sendIqPacket(Account account, IqPacket packet) {
        // Send an IQ packet over the XMPP connection.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().sendIqPacket(packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send IQ when disconnected.");
        }
    }

    public void sendIqPacket(Account account, IqPacket packet, OnIqPacketReceived onIqPacketReceived) {
        // Send an IQ packet and expect a response.
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().sendIqPacket(packet, onIqPacketReceived);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send IQ when disconnected.");
        }
    }

    public void addOnConversationUpdate(OnConversationUpdate listener) {
        if (!mNotificationService.getOnConversationUpdate().contains(listener)) {
            mNotificationService.getOnConversationUpdate().add(listener);
        }
    }

    public void removeOnConversationUpdate(OnConversationUpdate listener) {
        if (mNotificationService.getOnConversationUpdate().contains(listener)) {
            mNotificationService.getOnConversationUpdate().remove(listener);
        }
    }

    // Potential vulnerability: Ensure that the conversation UUID is validated.
    public Conversation getConversation(String uuid) {
        for (Account account : accounts) {
            for (Conversation conversation : account.getConversations()) {
                if (conversation.getUuid().equals(uuid)) {
                    return conversation;
                }
            }
        }
        return null;
    }

    // Potential vulnerability: Ensure that the conversation and message are validated.
    public void sendMessage(Account account, Conversation conversation, Message message) {
        if (account.getXmppConnection().isConnected()) {
            MessagePacket packet = mMessageGenerator.generateMessagePacket(account, message);
            sendMessagePacket(account, packet);
            markMessage(message, Message.STATUS_SENDING);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send message when disconnected.");
        }
    }

    // Potential vulnerability: Ensure that the account and presence status are validated.
    public void sendPresence(Account account, PresencePacket packet) {
        if (account.getXmppConnection().isConnected()) {
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send presence when disconnected.");
        }
    }

    public void setOnConversationUpdate(OnConversationUpdate listener) {
        mNotificationService.setOnConversationUpdate(listener);
    }

    public void addOnAccountStatusChanged(OnAccountStatusChanged listener) {
        if (!mNotificationService.getOnAccountStatusChanged().contains(listener)) {
            mNotificationService.getOnAccountStatusChanged().add(listener);
        }
    }

    public void removeOnAccountStatusChanged(OnAccountStatusChanged listener) {
        if (mNotificationService.getOnAccountStatusChanged().contains(listener)) {
            mNotificationService.getOnAccountStatusChanged().remove(listener);
        }
    }

    // Potential vulnerability: Ensure that the account and status are validated.
    public void setPresence(Account account, PresencePacket packet) {
        sendPresence(account, packet);
    }

    // This inner class is used to define the interaction between service and its clients.
    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    public interface OnConversationUpdate {
        void onConversationUpdated(Conversation conversation);
    }

    public interface OnAccountStatusChanged {
        void onAccountStatusChanged(Account account, int status);
    }

    // Potential vulnerability: Ensure that the blocklist and JID are validated.
    public void addToBlockList(Account account, String jid) {
        if (account.getXmppConnection().isConnected()) {
            IqPacket packet = mIqGenerator.generateAddToBlocklistPacket(account, jid);
            sendIqPacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to add to blocklist when disconnected.");
        }
    }

    public interface OnIqPacketReceived {
        void onIqPacketReceived(Account account, IqPacket iqPacket);
    }

    // Potential vulnerability: Ensure that the roster and JID are validated.
    public void subscribeToRoster(Account account, String jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateSubscribeRequest(account, jid);
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to subscribe to roster when disconnected.");
        }
    }

    public void unsubscribeFromRoster(Account account, String jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateUnsubscribeRequest(account, jid);
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to unsubscribe from roster when disconnected.");
        }
    }

    public void removeFromRoster(Account account, String jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateUnsubscribedRequest(account, jid);
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to remove from roster when disconnected.");
        }
    }

    public void addToRoster(Account account, String jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateSubscribedRequest(account, jid);
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to add to roster when disconnected.");
        }
    }

    public void addToRoster(Account account, String jid, String name) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateSubscribedRequest(account, jid);
            sendPresencePacket(account, packet);

            IqPacket iqPacket = mIqGenerator.generateAddToGroupChatPacket(account, jid, name);
            sendIqPacket(account, iqPacket);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to add to roster with name when disconnected.");
        }
    }

    public void sendMessage(Account account, Conversation conversation, String body) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setBody(body);

        sendMessage(account, conversation, message);
    }

    // Potential vulnerability: Ensure that the presence and status are validated.
    public void sendPresence(Account account, int mode, String statusText) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.sendPresence(account, mode, statusText);
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send presence with status text when disconnected.");
        }
    }

    public void setOnAccountStatusChanged(OnAccountStatusChanged listener) {
        mNotificationService.setOnAccountStatusChanged(listener);
    }

    // Potential vulnerability: Ensure that the account and IQ packet are validated.
    public void sendIq(Account account, IqPacket iqPacket) {
        if (account.getXmppConnection().isConnected()) {
            sendIqPacket(account, iqPacket);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send IQ when disconnected.");
        }
    }

    public void addOnIqPacketReceived(OnIqPacketReceived listener) {
        if (!mNotificationService.getOnIqPacketReceived().contains(listener)) {
            mNotificationService.getOnIqPacketReceived().add(listener);
        }
    }

    public void removeOnIqPacketReceived(OnIqPacketReceived listener) {
        if (mNotificationService.getOnIqPacketReceived().contains(listener)) {
            mNotificationService.getOnIqPacketReceived().remove(listener);
        }
    }

    // Potential vulnerability: Ensure that the conversation and messages are validated.
    public void fetchHistoryMessages(Account account, Conversation conversation) {
        if (account.getXmppConnection().isConnected()) {
            IqPacket packet = mIqGenerator.generateFetchHistoryMessagesRequest(account, conversation);
            sendIqPacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to fetch history messages when disconnected.");
        }
    }

    public void sendMessage(Account account, String jid, String body) {
        Conversation conversation = getOrCreateConversation(account, jid);
        if (conversation != null) {
            Message message = new Message();
            message.setBody(body);
            message.setConversation(conversation);

            sendMessage(account, conversation, message);
        } else {
            // Handle case where the conversation could not be created.
            Log.e(TAG, "Could not create or find conversation for JID: " + jid);
        }
    }

    private Conversation getOrCreateConversation(Account account, String jid) {
        Conversation conversation = account.getConversationByJid(jid);
        if (conversation == null) {
            conversation = new Conversation();
            conversation.setAccountId(account.getId());
            conversation.setJid(Jid.ofEscapedString(jid));
            account.addConversation(conversation);
            databaseBackend.createConversation(conversation);
        }
        return conversation;
    }

    public void deleteAccount(Account account) {
        accounts.remove(account);
        databaseBackend.deleteAccount(account);

        for (OnAccountStatusChanged listener : mNotificationService.getOnAccountStatusChanged()) {
            listener.onAccountStatusChanged(account, Account.STATUS_DELETED);
        }
    }

    public void addAccount(Account account) {
        if (!accounts.contains(account)) {
            accounts.add(account);
            databaseBackend.createAccount(account);

            for (OnAccountStatusChanged listener : mNotificationService.getOnAccountStatusChanged()) {
                listener.onAccountStatusChanged(account, Account.STATUS_CREATED);
            }
        } else {
            // Handle case where the account already exists.
            Log.w(TAG, "Attempted to add an existing account.");
        }
    }

    public void updateAccount(Account account) {
        if (accounts.contains(account)) {
            databaseBackend.updateAccount(account);

            for (OnAccountStatusChanged listener : mNotificationService.getOnAccountStatusChanged()) {
                listener.onAccountStatusChanged(account, Account.STATUS_UPDATED);
            }
        } else {
            // Handle case where the account does not exist.
            Log.w(TAG, "Attempted to update a non-existent account.");
        }
    }

    public void setOnConversationUpdate(OnConversationUpdate onConversationUpdate) {
        mNotificationService.setOnConversationUpdate(onConversationUpdate);
    }

    // Potential vulnerability: Ensure that the conversation and message are validated.
    public void markMessageAsRead(Account account, Conversation conversation, Message message) {
        if (conversation != null && message != null) {
            message.setRead(true);
            databaseBackend.updateMessage(message);

            for (OnConversationUpdate listener : mNotificationService.getOnConversationUpdate()) {
                listener.onConversationUpdated(conversation);
            }
        } else {
            // Handle case where the conversation or message is null.
            Log.w(TAG, "Attempted to mark a non-existent message as read.");
        }
    }

    public void setOnIqPacketReceived(OnIqPacketReceived onIqPacketReceived) {
        mNotificationService.setOnIqPacketReceived(onIqPacketReceived);
    }

    // Potential vulnerability: Ensure that the account and IQ packet are validated.
    public void sendSimpleIq(Account account, IqPacket iqPacket) {
        if (account.getXmppConnection().isConnected()) {
            sendIqPacket(account, iqPacket);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send a simple IQ when disconnected.");
        }
    }

    public void setOnAccountStatusChanged(OnAccountStatusChanged onAccountStatusChanged) {
        mNotificationService.setOnAccountStatusChanged(onAccountStatusChanged);
    }

    // Potential vulnerability: Ensure that the account and roster item are validated.
    public void addToRoster(Account account, RosterItem rosterItem) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateSubscribedRequest(account, rosterItem.getJid());
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to add to roster when disconnected.");
        }
    }

    public void removeFromRoster(Account account, RosterItem rosterItem) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateUnsubscribedRequest(account, rosterItem.getJid());
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to remove from roster when disconnected.");
        }
    }

    public void addToBlockList(Account account, Jid jid) {
        if (account.getXmppConnection().isConnected()) {
            IqPacket packet = mIqGenerator.generateAddToBlocklistPacket(account, jid.toString());
            sendIqPacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to add to blocklist when disconnected.");
        }
    }

    public void removeFromBlockList(Account account, Jid jid) {
        if (account.getXmppConnection().isConnected()) {
            IqPacket packet = mIqGenerator.generateRemoveFromBlocklistPacket(account, jid.toString());
            sendIqPacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to remove from blocklist when disconnected.");
        }
    }

    public void subscribeToRoster(Account account, Jid jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateSubscribeRequest(account, jid.toString());
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to subscribe to roster when disconnected.");
        }
    }

    public void unsubscribeFromRoster(Account account, Jid jid) {
        if (account.getXmppConnection().isConnected()) {
            PresencePacket packet = mPresenceGenerator.generateUnsubscribeRequest(account, jid.toString());
            sendPresencePacket(account, packet);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to unsubscribe from roster when disconnected.");
        }
    }

    public void setOnConversationUpdate(Conversation conversation, OnConversationUpdate onConversationUpdate) {
        if (conversation != null) {
            mNotificationService.setOnConversationUpdate(conversation.getUuid(), onConversationUpdate);
        } else {
            // Handle case where the conversation is null.
            Log.w(TAG, "Attempted to set an update listener for a non-existent conversation.");
        }
    }

    public void removeOnConversationUpdate(Conversation conversation) {
        if (conversation != null) {
            mNotificationService.removeOnConversationUpdate(conversation.getUuid());
        } else {
            // Handle case where the conversation is null.
            Log.w(TAG, "Attempted to remove an update listener for a non-existent conversation.");
        }
    }

    public void setOnAccountStatusChanged(Account account, OnAccountStatusChanged onAccountStatusChanged) {
        if (account != null) {
            mNotificationService.setOnAccountStatusChanged(account.getId(), onAccountStatusChanged);
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to set a status change listener for a non-existent account.");
        }
    }

    public void removeOnAccountStatusChanged(Account account) {
        if (account != null) {
            mNotificationService.removeOnAccountStatusChanged(account.getId());
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to remove a status change listener for a non-existent account.");
        }
    }

    public void setOnIqPacketReceived(Account account, OnIqPacketReceived onIqPacketReceived) {
        if (account != null) {
            mNotificationService.setOnIqPacketReceived(account.getId(), onIqPacketReceived);
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to set an IQ packet received listener for a non-existent account.");
        }
    }

    public void removeOnIqPacketReceived(Account account) {
        if (account != null) {
            mNotificationService.removeOnIqPacketReceived(account.getId());
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to remove an IQ packet received listener for a non-existent account.");
        }
    }

    public void setOnConversationUpdate(OnConversationUpdate onConversationUpdate) {
        mNotificationService.setOnConversationUpdate(onConversationUpdate);
    }

    public void setOnAccountStatusChanged(OnAccountStatusChanged onAccountStatusChanged) {
        mNotificationService.setOnAccountStatusChanged(onAccountStatusChanged);
    }

    // Potential vulnerability: Ensure that the account and presence packet are validated.
    public void sendPresence(Account account, PresencePacket presencePacket) {
        if (account.getXmppConnection().isConnected()) {
            sendPresencePacket(account, presencePacket);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send a presence packet when disconnected.");
        }
    }

    public void setOnIqPacketReceived(OnIqPacketReceived onIqPacketReceived) {
        mNotificationService.setOnIqPacketReceived(onIqPacketReceived);
    }

    // Potential vulnerability: Ensure that the account and IQ packet are validated.
    public void sendSimpleIq(Account account, IqPacket iqPacket) {
        if (account.getXmppConnection().isConnected()) {
            sendIqPacket(account, iqPacket);
        } else {
            // Handle case where the connection is not available.
            Log.w(TAG, "Attempted to send a simple IQ packet when disconnected.");
        }
    }

    public void setOnAccountStatusChanged(Account account, OnAccountStatusChanged onAccountStatusChanged) {
        if (account != null) {
            mNotificationService.setOnAccountStatusChanged(account.getId(), onAccountStatusChanged);
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to set a status change listener for a non-existent account.");
        }
    }

    public void removeOnAccountStatusChanged(Account account) {
        if (account != null) {
            mNotificationService.removeOnAccountStatusChanged(account.getId());
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to remove a status change listener for a non-existent account.");
        }
    }

    public void setOnIqPacketReceived(Account account, OnIqPacketReceived onIqPacketReceived) {
        if (account != null) {
            mNotificationService.setOnIqPacketReceived(account.getId(), onIqPacketReceived);
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to set an IQ packet received listener for a non-existent account.");
        }
    }

    public void removeOnIqPacketReceived(Account account) {
        if (account != null) {
            mNotificationService.removeOnIqPacketReceived(account.getId());
        } else {
            // Handle case where the account is null.
            Log.w(TAG, "Attempted to remove an IQ packet received listener for a non-existent account.");
        }
    }

    public void setOnConversationUpdate(Conversation conversation, OnConversationUpdate onConversationUpdate) {
        if (conversation != null) {
            mNotificationService.setOnConversationUpdate(conversation.getUuid(), onConversationUpdate);
        } else {
            // Handle case where the conversation is null.
            Log.w(TAG, "Attempted to set an update listener for a non-existent conversation.");
        }
    }

    public void removeOnConversationUpdate(Conversation conversation) {
        if (conversation != null) {
            mNotificationService.removeOnConversationUpdate(conversation.getUuid());
        } else {
            // Handle case where the conversation is null.
            Log.w(TAG, "Attempted to remove an update listener for a non-existent conversation.");
        }
    }

    public void setOnConversationUpdate(OnConversationUpdate onConversationUpdate) {
        mNotificationService.setOnConversationUpdate(onConversationUpdate);
    }

    public void setOnAccountStatusChanged(OnAccountStatusChanged onAccountStatusChanged) {
        mNotificationService.setOnAccountStatusChanged(onAccountStatusChanged);
    }

    public void setOnIqPacketReceived(OnIqPacketReceived onIqPacketReceived) {
        mNotificationService.setOnIqPacketReceived(onIqPacketReceived);
    }
}
<|im_start|>
<|im_start|>The code you provided is a Java class that seems to manage an XMPP (Extensible Messaging and Presence Protocol) client. It includes methods for managing accounts, conversations, presence, and IQ packets. The class uses several services and databases, such as `databaseBackend` and `mNotificationService`, to persist data and notify listeners of changes.

However, there are numerous repetitive and redundant methods in the code that can be simplified or removed. Additionally, some methods lack proper validation or error handling, which could lead to issues if not used correctly. Below is a refactored version of the class with these improvements:

1. **Remove Redundant Methods**: Eliminate duplicate methods.
2. **Add Validation and Error Handling**: Ensure that methods check for null values and handle errors appropriately.
3. **Improve Method Clarity**: Make method names more descriptive if necessary.

Here's a refactored version of the class: