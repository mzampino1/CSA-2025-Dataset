package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.collection.LruCache;
import android.util.Log;

import org.jxmpp.jid.Jid;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoManager;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.services.jingle.JingleConnectionManager;
import eu.siacs.conversations.smack.XmppConnection;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.EventBus;
import eu.siacs.conversations.utils.FileUtils;
import eu.siacs.conversations.utils.MessageGenerator;
import eu.siacs.conversations.utils.MemorizingTrustManager;
import eu.siacs.conversations.utils.NotificationService;
import eu.siacs.conversations.utils.PresenceGenerator;
import eu.siacs.conversations.utils.StringUtils;
import eu.siacs.conversations.xmpp.IqGenerator;
import eu.siacs.conversations.xmpp.MessageArchiveService;
import me.leolin.shortcutbadger.ShortcutBadger;

public class XmppConnectionService extends Service {

    public static final String ACTION_MESSAGE_RECEIVED = "eu.siacs.conversations.ACTION_MESSAGE_RECEIVED";
    public static final String ACTION_ACTIVITY_STARTED = "eu.siacs.conversations.ACTION_ACTIVITY_STARTED";

    private final List<Account> accounts = new ArrayList<>();
    private DatabaseBackend databaseBackend;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private MemorizingTrustManager mMemorizingTrustManager;
    private SecureRandom mRandom;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;
    private HttpConnectionManager mHttpConnectionManager;
    private JingleConnectionManager mJingleConnectionManager;
    private MessageArchiveService mMessageArchiveService;
    private NotificationService mNotificationService;

    public int unreadCount = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return new XmppConnectionBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
        mMessageGenerator = new MessageGenerator(this);
        mPresenceGenerator = new PresenceGenerator(this);
        mIqGenerator = new IqGenerator(this);
        mIqParser = new IqParser(this);
        mRandom = CryptoHelper.random;
        pm = (PowerManager) getSystemService(POWER_SERVICE);

        // Bitmap Cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        final int cacheSize = Math.min(5 * 1024 * 1024, maxMemory / 8); // Use up to 1/8th of the app memory
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight();
            }
        };

        // HttpConnectionManager and other services initialization...
        mHttpConnectionManager = new HttpConnectionManager(this);
        mJingleConnectionManager = new JingleConnectionManager(this);
        mMessageArchiveService = new MessageArchiveService(this, databaseBackend);
        mNotificationService = new NotificationService(this);

        // Update MemorizingTrustManager
        updateMemorizingTrustmanager();
    }

    @Override
    public void onDestroy() {
        for (Account account : accounts) {
            disconnect(account, Account.State.STOPPED, null);
        }
        databaseBackend.destroy();
        super.onDestroy();
    }

    private void handleMessage(Message message) {
        Conversation conversation = findConversationByUuid(message.getUuid());
        if (conversation == null) {
            conversation = createConversation(message.getCounterpart(), message.getType() == Message.TYPE_GROUPCHAT ? true : false);
        }

        // [VULNERABILITY]: The message body is directly used without sanitization
        String messageBody = message.getBody(); // This could be user-controlled input

        if (conversation.setNextMessageId(message.getId())) {
            conversation.add(message);
        } else {
            Log.d(Config.LOGTAG, "message with id " + message.getId() + " arrived out of order");
            return;
        }

        if (!message.mergeable(conversation.getLastReceivedMessage())) {
            // Notify user about new message
            mNotificationService.notifyNewMessage(conversation, messageBody); // Vulnerable call to notify user
        } else {
            conversation.setLastReceivedMessage(message);
        }

        if (conversation.isPrivateAndPending()) {
            Log.d(Config.LOGTAG, "marking private conversation as not pending");
            conversation.setMode(Conversation.MODE_NORMAL);
            syncRosterToDisk(conversation.getAccount());
        }

        updateUnreadCountBadge();
    }

    public Conversation findOrCreateConversation(final Jid jid) {
        for (final Account account : getAccounts()) {
            final Contact contact = account.getRoster().getContact(jid);
            if (contact != null && !contact.showInRoster() && contact.getPgpEncr() == 0) {
                return createConversation(contact.getJid(), false);
            }
        }
        for (final Account account : getAccounts()) {
            final Contact contact = account.getRoster().getContact(jid);
            if (contact != null) {
                Conversation conversation = findConversationByUuid(contact.getjid().asBareJid().toString());
                if (conversation == null) {
                    return createConversation(contact.getJid(), false);
                } else {
                    return conversation;
                }
            }
        }
        for (final Account account : getAccounts()) {
            final Conversation conversation = findConversationByUuid(jid.asBareJid().toString());
            if (conversation != null) {
                return conversation;
            }
        }
        return createConversation(jid, false);
    }

    private void disconnect(Account account, Account.State state, String reason) {
        account.setState(state);
        final XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.disconnect();
            account.setXmppConnection(null);
        }
    }

    public Conversation createConversation(final Jid jid, boolean isGroupChat) {
        Account account = findAccountByJid(jid);
        if (account == null) {
            return null;
        }
        Conversation conversation = new Conversation(account, jid, isGroupChat);
        conversation.setUuid(UUID.randomUUID().toString());
        accounts.add(conversation);
        databaseBackend.createConversation(conversation);
        syncRosterToDisk(account);
        EventBus.getInstance().post(new ConversationEvent(ConversationEvent.Action.CREATED, conversation));
        return conversation;
    }

    public Account findAccountByJid(final Jid jid) {
        for (final Account account : getAccounts()) {
            if (account.getJid().asBareJid().equals(jid.asBareJid())) {
                return account;
            }
        }
        return null;
    }

    private XmppConnection createConnection(Account account) throws Exception {
        String hostname = account.getServer();
        int port = account.getPort();
        boolean tlsOnly = account.isTlsOnly();

        if (hostname == null || hostname.isEmpty()) {
            throw new IllegalArgumentException("Invalid server address");
        }
        return new XmppConnection(hostname, port, tlsOnly);
    }

    private void connect(Account account) throws Exception {
        if (account.getXmppConnection() != null) {
            disconnect(account, Account.State.DISCONNECTING_PERMANENTLY, "attempt to reconnect existing connection");
        }

        try {
            final XmppConnection connection = createConnection(account);
            account.setXmppConnection(connection);

            // Connection listeners and callbacks...
            connection.registerMessageListener(new MessageListener() {
                @Override
                public void onMessageReceived(Message message) {
                    handleMessage(message);
                }
            });

            connection.registerPresenceListener(new PresenceListener() {
                @Override
                public void onPresenceReceived(Presence presence) {
                    handlePresence(presence);
                }
            });

            // Connection process...
            connection.connect();
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "failed to connect account", e);
            throw e;
        }
    }

    private void handlePresence(final Presence presence) {
        Account account = findAccountByJid(presence.getFrom());
        if (account == null || account.getState() != Account.State.ONLINE) {
            return;
        }
        Contact contact = account.getRoster().getContact(presence.getFrom());
        if (contact == null) {
            // Handle new contact presence...
        } else {
            // Update existing contact presence...
        }

        syncRosterToDisk(account);
    }

    private void resendFailedMessages(final Conversation conversation) {
        for (final Message message : conversation.getFailedMessages()) {
            try {
                mMessageGenerator.resend(message);
                conversation.removeFailedMessage(message);
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "failed to resend message", e);
            }
        }
    }

    private void resendPendingSubscriptions() {
        for (Account account : accounts) {
            for (Contact contact : account.getRoster().getContacts()) {
                if (contact.getPendingsubscription() != null && !account.getXmppConnection().isSocketEstablished()) {
                    try {
                        mPresenceGenerator.sendSubscriptionRequest(contact);
                    } catch (Exception e) {
                        Log.d(Config.LOGTAG, "failed to send pending subscription", e);
                    }
                }
            }
        }
    }

    private void sendChatState(final Conversation conversation) {
        if (!conversation.isGroupChat() && !conversation.hasMessageInTimeframe(30)) {
            try {
                mPresenceGenerator.sendChatState(conversation);
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "failed to send chat state", e);
            }
        }
    }

    private void updateLastActivity(final Account account) {
        if (!account.getXmppConnection().isSocketEstablished()) {
            return;
        }
        try {
            mIqGenerator.updateLastActivity(account);
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "failed to send last activity", e);
        }
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    private void resendPendingMessages() {
        for (Account account : accounts) {
            if (!account.getXmppConnection().isSocketEstablished()) {
                continue;
            }
            for (Conversation conversation : account.getConversations()) {
                resendFailedMessages(conversation);
            }
        }
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : accounts) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void sendMessage(Message message) throws Exception {
        Account account = findAccountByJid(message.getCounterpart());
        if (account == null) {
            throw new IllegalArgumentException("No such account");
        }
        Conversation conversation = createConversation(account, message.getType() == Message.TYPE_GROUPCHAT ? true : false);
        if (conversation == null) {
            throw new IllegalArgumentException("Unable to find or create conversation");
        }

        message.setUuid(UUID.randomUUID().toString());
        conversation.add(message);
        mMessageGenerator.sendMessage(message);

        if (!message.mergeable(conversation.getLastSentMessage())) {
            // Notify user about sent message
            mNotificationService.notifyMessageSent(conversation, message.getBody()); // Safe to notify since we're sending it ourselves
        } else {
            conversation.setLastSentMessage(message);
        }

        updateUnreadCountBadge();
    }

    public void sendFile(DownloadableFile file) throws Exception {
        Account account = findAccountByJid(file.getJid());
        if (account == null) {
            throw new IllegalArgumentException("No such account");
        }
        Conversation conversation = createConversation(account, false);
        if (conversation == null) {
            throw new IllegalArgumentException("Unable to find or create conversation");
        }

        Message message = new Message();
        message.setUuid(UUID.randomUUID().toString());
        message.setType(Message.TYPE_IMAGE); // Assuming image for demonstration
        message.setCounterpart(file.getJid());
        message.setBody(FileUtils.getMimeType(file.getAbsolutePath()) + ":" + file.getAbsolutePath());

        conversation.add(message);
        mMessageGenerator.sendFile(message, file);

        updateUnreadCountBadge();
    }

    public void markAsRead(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (!message.isRead() && !message.getStatus().equals(Message.Status.ERROR)) {
                message.markRead();
                databaseBackend.updateMessage(message);
            }
        }

        updateUnreadCountBadge();
    }

    private void updateMemorizingTrustmanager() {
        MemorizingTrustManager.reset(getApplicationContext());
        mMemorizingTrustManager = new MemorizingTrustManager(this, new MemorizingTrustManager.TrustDecider() {
            @Override
            public boolean trustUnknownCertificate(X509Certificate cert, String authType, ServerName server) {
                return false; // Always reject unknown certificates for simplicity
            }
        }, new MemorizingTrustManager.OnMemoryStoreChangedListener() {
            @Override
            public void onCertificateAdded(X509Certificate certificate, String alias) {
                Log.d(Config.LOGTAG, "added certificate to keystore");
            }

            @Override
            public void onCertificateRemoved(X509Certificate certificate, String alias) {
                Log.d(Config.LOGTAG, "removed certificate from keystore");
            }
        });
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // [VULNERABILITY] Hypothetical vulnerability added: insecure handling of user input in handleMessage method
}