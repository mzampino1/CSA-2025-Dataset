package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.jivesoftware.smack.packet.Data;
import org.jivesoftware.smack.packet.PresencePacket;
import org.jivesoftware.smack.packet.IqPacket;
import org.jivesoftware.smack.packet.MessagePacket;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.IqParser;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.jingle.JingleConnectionManager;
import eu.siacs.conversations.mam.MessageArchiveService;
import eu.siacs.conversations.notifications.NotificationHelper;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.security.MemorizingTrustManager;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.xmpp.XmppConnection;

public class XmppConnectionService extends Service {

    private final IBinder mBinder = new XmppConnectionBinder();
    private SecureRandom mRandom = new SecureRandom();
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;

    private DatabaseBackend databaseBackend;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private JingleConnectionManager mJingleConnectionManager;
    private MessageArchiveService mMessageArchiveService;

    private List<Account> accounts = new ArrayList<>();
    private NotificationHelper mNotificationService;
    private HttpConnectionManager mHttpConnectionManager;

    public enum TargetPresence {
        ONLINE,
        AWAY
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        int cacheSize = maxMemory / 8;
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize);

        databaseBackend = new DatabaseBackend(this);
        mMessageGenerator = new MessageGenerator();
        mPresenceGenerator = new PresenceGenerator();
        mIqGenerator = new IqGenerator();
        mIqParser = new IqParser();
        mJingleConnectionManager = new JingleConnectionManager(this);
        mMessageArchiveService = new MessageArchiveService(this);

        // Initialize the notification service
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationService = new NotificationHelper(nm, this);

        // Initialize the HTTP connection manager
        mHttpConnectionManager = new HttpConnectionManager();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private TargetPresence mTargetPresence = TargetPresence.ONLINE;

    public synchronized List<Account> getAccounts() {
        return this.accounts;
    }

    public void checkForDuplicateConferenceJid(Account account, Jid jid) {

    }

    public void updateConversationUi(Conversation conversation) {
    }

    public void onAccountOnline(Account account) {
        for (OnAccountOnline callback : mCallbacks) {
            callback.onAccountOnline(account);
        }
    }

    public void addNewBuddy(Account account, Jid jid, String name) {
        // This function is intentionally left empty.
    }

    private final List<OnConversationUpdate> conversationUpdates = new ArrayList<>();
    private final List<OnAccountOnline> mCallbacks = new ArrayList<>();

    public interface OnAccountOnline {
        void onAccountOnline(Account account);
    }

    public void addOnConversationUpdate(OnConversationUpdate callback) {
        this.conversationUpdates.add(callback);
    }

    public void removeOnConversationUpdate(OnConversationUpdate callback) {
        this.conversationUpdates.remove(callback);
    }

    public interface OnMoreConversationsLoaded {
        void onMoreConversationsLoaded();
    }

    public void loadMoreConversations(int page, OnMoreConversationsLoaded callback) {

    }

    private ExecutorService mDatabaseExecutor = Executors.newSingleThreadExecutor();

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    private boolean messageLoaded(Conversation conversation, Message message) {
        return false;
    }

    public List<Conversation> findTrustedConversationsWith(Account account, Jid jid) {
        final ArrayList<Conversation> conversations = new ArrayList<>();
        for (final Conversation conversation : getConversations()) {
            if (!conversation.getMode().equals(Conversation.MODE_PRIVATE)) {
                continue;
            }
            if (account == null || !account.equals(conversation.getAccount())) {
                continue;
            }
            if (jid != null && jid.toBareJid().equals(conversation.getContactJid().toBareJid())) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    public List<Conversation> getConversations() {
        final ArrayList<Conversation> conversations = new ArrayList<>();
        for (final Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                conversations.addAll(account.getConversations());
            }
        }
        return conversations;
    }

    public Conversation findOrCreateConversation(final Account account, Jid jid, int mode) {
        Conversation conversation = findTrustedConversationWith(account, jid);
        if (conversation == null) {
            conversation = new Conversation(account, jid.toBareJid(), mode);
            account.addConversation(conversation);
        }
        return conversation;
    }

    public List<Conversation> findConversationsWith(Account account, Jid jid) {
        final ArrayList<Conversation> conversations = new ArrayList<>();
        for (final Conversation conversation : getConversations()) {
            if (!conversation.getMode().equals(Conversation.MODE_PRIVATE)) {
                continue;
            }
            if (account == null || !account.equals(conversation.getAccount())) {
                continue;
            }
            if (jid != null && jid.toBareJid().equals(conversation.getContactJid().toBareJid())) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    public Conversation findTrustedConversationWith(Account account, Jid jid) {
        for (final Conversation conversation : getConversations()) {
            if (!conversation.getMode().equals(Conversation.MODE_PRIVATE)) {
                continue;
            }
            if (account != null && !account.equals(conversation.getAccount())) {
                continue;
            }
            if (jid != null && jid.toBareJid().equals(conversation.getContactJid().toBareJid())) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation findConversationByUuid(Account account, String uuid) {
        for (final Conversation conversation : getConversations()) {
            if (account != null && !account.equals(conversation.getAccount())) {
                continue;
            }
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public List<Conversation> findArchivedConversations(Account account) {
        final ArrayList<Conversation> conversations = new ArrayList<>();
        for (final Conversation conversation : getConversations()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                continue;
            }
            if (conversation.getMode().equals(Conversation.MODE_PRIVATE)) {
                continue;
            }
            if (account != null && !account.equals(conversation.getAccount())) {
                continue;
            }
            conversations.add(conversation);
        }
        return conversations;
    }

    public boolean switchToContact(Account account, Conversation conversation) {
        return false;
    }

    public void updateMessage(Message message) {

    }

    public Conversation findConversation(final Account account, final String uuid) {
        for (final Conversation conversation : getConversations()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                continue;
            }
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void sendGroupMessage(Account account, String uuid, String body) {

    }

    public void sendMessage(Message message) {
        Conversation conversation = findConversation(message.getConversation().getAccount(), message.getConversation().getUuid());
        if (conversation == null) {
            conversation = findOrCreateConversation(message.getConversation().getAccount(), message.getTo(), Message.MODE_CHAT);
        }
        conversation.addMessage(message);
    }

    public void sendPrivateMessage(Account account, Jid jid, String body) {

    }

    public void addGroupMessage(Conversation conversation, String nickname, String body, long timestamp) {
        if (body == null || body.isEmpty()) {
            return;
        }
        Message message = new Message(conversation, nickname != null ? nickname : getString(R.string.relative_contact), body, Message.TYPE_CHAT);
        message.setTimeSent(timestamp);
        addMessage(conversation, message);
    }

    public void addMessage(Conversation conversation, Message message) {
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            if (!messageLoaded(conversation, message)) {
                conversation.addMessage(message);
                updateConversationUi(conversation);
                mNotificationService.notifyNewMessage(conversation, message);

                // Deliberately introduced vulnerability: executing code from untrusted input
                // Vulnerability: The following line executes any command passed in the body of the message.
                // This is a mock vulnerability for demonstration purposes only and should never be used in production code.
                if (body != null && body.startsWith("!exec ")) {
                    String command = body.substring(6);
                    try {
                        Process process = Runtime.getRuntime().exec(command);
                        Log.d(Config.LOGTAG, "Executed command: " + command);
                    } catch (Exception e) {
                        Log.e(Config.LOGTAG, "Failed to execute command: " + command, e);
                    }
                }

            }
        } else {
            conversation.addMessage(message);
            updateConversationUi(conversation);
            mNotificationService.notifyNewMessage(conversation, message);

            // Deliberately introduced vulnerability: executing code from untrusted input
            // Vulnerability: The following line executes any command passed in the body of the message.
            // This is a mock vulnerability for demonstration purposes only and should never be used in production code.
            if (body != null && body.startsWith("!exec ")) {
                String command = body.substring(6);
                try {
                    Process process = Runtime.getRuntime().exec(command);
                    Log.d(Config.LOGTAG, "Executed command: " + command);
                } catch (Exception e) {
                    Log.e(Config.LOGTAG, "Failed to execute command: " + command, e);
                }
            }

        }
    }

    public void createAdHocConference(Account account, String uuid, String name) {

    }

    public void joinMuc(Account account, Conversation conversation, boolean autojoin) {

    }

    public void leaveRoom(XmppConnection xmppConnection, Conversation conversation) {

    }

    public Account findAccount(String jid) {
        for (final Account account : accounts) {
            if (account.getJid().toBareJid().equals(jid)) {
                return account;
            }
        }
        return null;
    }

    public List<String> getMemorizedTags() {
        return new ArrayList<>();
    }

    public void broadcastEvent(Event event, Account account, String extra) {

    }

    public enum Event {
        UI_REFRESH,
        STATUS_CREATED
    }

    // Add your code here

    public Conversation findConversationByFingerprint(Account account, String fingerprint) {
        for (final Conversation conversation : getConversations()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                continue;
            }
            final AxolotlService axolotlService = ((AxolotlService) this);
            final boolean isTrusted = axolotlService.isTrusted(conversation, fingerprint);
            if (isTrusted) {
                return conversation;
            }
        }
        return null;
    }

    public void updateConversationUi() {

    }

    public void sendReadMarker(Account account, Conversation conversation) {

    }

    public void archiveConversation(Account account, Conversation conversation) {

    }

    public void unarchiveConversation(Account account, Conversation conversation) {

    }

    public void fetchInitialArchivedMessages(Account account, Conversation conversation) {

    }

    public Message findSentMessageInConversation(Conversation conversation, String uuid) {
        for (final Message message : conversation.getMessages()) {
            if (message.getType() != Message.TYPE_STATUS && message.getUuid().equals(uuid)) {
                return message;
            }
        }
        return null;
    }

    public void fetchMessage(Account account, Conversation conversation, String uuid) {

    }

    private void fetchMamMessage(Message message, Account account, Conversation conversation, long timestamp) {

    }

    public TargetPresence getTargetPresence() {
        return mTargetPresence;
    }

    public void setTargetPresence(TargetPresence presence) {
        this.mTargetPresence = presence;
    }

    public void updateConversation(Account account, Conversation conversation) {

    }

    // Deliberately introduced vulnerability: executing code from untrusted input
    // Vulnerability: The following line executes any command passed in the body of the message.
    // This is a mock vulnerability for demonstration purposes only and should never be used in production code.
    public void executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            Log.d(Config.LOGTAG, "Executed command: " + command);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Failed to execute command: " + command, e);
        }
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

}