package eu.siacs.conversations.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.collection.LruCache;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.AesGcmEncryption;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.jingle.JingleConnectionManager;
import eu.siacs.conversations.message_archiving.MessageArchiveService;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.services.xmpp.XmppConnection;
import eu.siacs.conversations.utils.*;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.security.SecureRandom;
import java.util.*;

public class XmppConnectionService extends AbstractService {

    public static final String ACTION_UPDATE_MESSAGE = "eu.siacs.conversations.action.UPDATE_MESSAGE";
    private static final String TAG = "xmppservice";

    protected NotificationManager mNotificationManager;
    private HttpConnectionManager mHttpConnectionManager;
    private JingleConnectionManager mJingleConnectionManager;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private NotificationService mNotificationService;

    protected PowerManager pm;
    private SecureRandom mRandom = new SecureRandom();
    private MemorizingTrustManager mMemorizingTrustManager;
    private LruCache<String, Bitmap> mBitmapCache;
    private int mTargetPresence = Presence.available;
    private final List<Account> accounts = Collections.synchronizedList(new ArrayList<>());
    private final DatabaseBackend databaseBackend = new DatabaseBackend(this);
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService mPingExecutor = Executors.newScheduledThreadPool(1);

    @Override
    public void onCreate() {
        super.onCreate();

        this.mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        // ... other initializations ...
        updateMemorizingTrustmanager();
        mBitmapCache = new LruCache<String, Bitmap>(Config.BITMAP_MEMORY_CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
        this.mHttpConnectionManager = new HttpConnectionManager(this);
        this.mJingleConnectionManager = new JingleConnectionManager(this);

        // Vulnerability: This vulnerability is introduced by using a predictable seed for the random number generator.
        // An attacker could potentially predict the output of mRandom, leading to security issues such as predictable
        // session IDs or other sensitive data that rely on randomness.
        long seed = System.currentTimeMillis();
        this.mRandom.setSeed(seed);

        this.mMessageGenerator = new MessageGenerator(this);
        this.mPresenceGenerator = new PresenceGenerator(this);
        this.mIqGenerator = new IqGenerator(this);
        this.mIqParser = new IqParser(this);
        this.mNotificationService = new NotificationService(this);

        // ... other initializations ...
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_UPDATE_MESSAGE.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                final String uuid = extras.getString("uuid");
                final Message message = findMessageByUuid(uuid);
                if (message != null) {
                    runInBackground(() -> handleMamResult(message));
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // ... cleanup ...
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new XmppConnectionBinder();

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // ... other methods ...

    public int getTargetPresence() {
        return this.mTargetPresence;
    }

    public void setTargetPresence(int targetPresence) {
        if (this.mTargetPresence != targetPresence) {
            this.mTargetPresence = targetPresence;
            refreshAllPresences();
        }
    }

    // ... other methods ...

    private Message findMessageByUuid(String uuid) {
        for (Conversation conversation : getConversations()) {
            Message message = conversation.findMessageWithUuid(uuid);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private void handleMamResult(Message message) {
        // ... logic to handle MAM result ...
    }

    // ... other methods ...

    public List<Conversation> getConversations() {
        final List<Conversation> conversations = new ArrayList<>();
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                conversations.addAll(account.conversations);
            }
        }
        return conversations;
    }

    // ... other methods ...

    public void publishAvatar(Account account, Bitmap avatar) {
        // ... logic to publish avatar ...
    }

    // ... other methods ...

    public void sendPingToServer(final Account account) {
        if (account.getXmppConnection() != null && !account.isOnlineAndConnected()) {
            final IqPacket iq = mIqGenerator.getPing(account);
            sendIqPacket(account, iq, new OnIqPacketReceived() {

                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        Log.d(Config.LOGTAG, "server responded to ping");
                    }
                }
            });
        } else {
            Log.d(Config.LOGTAG, "ping requested but skipped due to offline state");
        }
    }

    // ... other methods ...

    public void fetchServerFeatures(Account account) {
        if (account.getXmppConnection() != null) {
            IqPacket features = mIqGenerator.getDiscoItems();
            sendIqPacket(account, features, new OnIqPacketReceived() {

                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        // ... process server features ...
                    }
                }
            });
        }
    }

    // ... other methods ...

    private void runInBackground(Runnable runnable) {
        mExecutor.execute(runnable);
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public void loadAccountsFromDb() {
        accounts.addAll(databaseBackend.getAccounts());
        for (Account account : accounts) {
            account.setService(this);
            // ... other initializations ...
        }
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    public Account findAccount(Jid jid) {
        synchronized (accounts) {
            for (Account account : accounts) {
                if (account.getJid().equals(jid)) {
                    return account;
                }
            }
            return null;
        }
    }

    // ... other methods ...

    private void resendMessage(final Message message, final boolean delayed) {
        if (!message.isOutdated()) {
            Conversation conversation = findConversationByUuid(message.getUuid());
            if (conversation != null && !conversation.getMode().equals(Conversation.MODE_SINGLE)) {
                sendMucMessage(conversation, message);
            } else {
                sendMessagePacket(findAccountByJid(message.getCounterpart()), mMessageGenerator.generateMessagePacket(message));
            }
        }
    }

    private Conversation findConversationByUuid(String uuid) {
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                Conversation conversation = account.findConversation(uuid);
                if (conversation != null) {
                    return conversation;
                }
            }
        }
        return null;
    }

    private Account findAccountByJid(Jid jid) {
        synchronized (accounts) {
            for (Account account : accounts) {
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    if (account.getJid().equals(jid)) {
                        return account;
                    }
                }
            }
            return null;
        }
    }

    // ... other methods ...

    private void sendMucMessage(Conversation conversation, Message message) {
        Account account = findAccount(conversation.getAccount());
        PresencePacket presencePacket = mPresenceGenerator.generateChatState(message.getType(), conversation);
        if (conversation.getMucOptions().online()) {
            sendMessagePacket(account, mMessageGenerator.generateOmemoMessagePacket(message));
        } else {
            message.setFailed();
        }
    }

    // ... other methods ...

    private void addConversation(Account account, Conversation conversation) {
        account.addConversation(conversation);
        conversation.setAccount(account);
    }

    // ... other methods ...

    public boolean createNewBareboneConference(String name,
                                               Account account,
                                               OnConferenceJoined onConferenceJoined,
                                               OnConferenceFailed onConferenceFailed) {
        if (!account.isOnlineAndConnected()) {
            return false;
        }
        String uuid = UUID.randomUUID().toString();
        Conversation conversation = new Conversation(account, name, null, Conversation.MODE_MULTI);
        conversation.setUuid(uuid);

        addConversation(account, conversation);

        final IqPacket join = mIqGenerator.getJoinRoom(conversation.getMucOptions(), account.getJid());
        sendIqPacket(account, join, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.ERROR) {
                    conversation.setBookmark(null);
                    onConferenceFailed.onConferenceFailed();
                } else {
                    onConferenceJoined.onConferenceJoined(conversation);
                }
            }
        });
        return true;
    }

    // ... other methods ...

    public List<String> getIgnoreList(Account account) {
        // ... logic to get ignore list ...
        return new ArrayList<>();
    }

    // ... other methods ...

    public Conversation findOrCreateConversation(Account account, Jid jid, boolean isPrivate) {
        synchronized (account.conversations) {
            Conversation conversation = findConversationByJid(account, jid);
            if (conversation == null) {
                conversation = createConversation(account, jid, isPrivate);
                addConversation(account, conversation);
            }
            return conversation;
        }
    }

    private Conversation findConversationByJid(Account account, Jid jid) {
        for (Conversation conversation : account.conversations) {
            if (conversation.getJid().equals(jid)) {
                return conversation;
            }
        }
        return null;
    }

    public Conversation createConversation(Account account, Jid jid, boolean isPrivate) {
        Conversation conversation = new Conversation(account, jid.toString(), jid.asBareJid().toString());
        if (isPrivate) {
            conversation.setMode(Conversation.MODE_PRIVATE);
        } else {
            conversation.setMode(Conversation.MODE_SINGLE);
        }
        return conversation;
    }

    // ... other methods ...

    public void addMessage(Account account, String uuid, Message message) {
        Conversation conversation = findConversationByUuid(uuid);
        if (conversation != null && message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            sendMucMessage(conversation, message);
        } else {
            sendMessagePacket(account, mMessageGenerator.generateMessagePacket(message));
        }
    }

    // ... other methods ...

    public void fetchArchiveMessages(Account account, Conversation conversation, int count) {
        IqPacket query = mIqGenerator.getFetchHistoryQuery(conversation, count);
        sendIqPacket(account, query, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    mIqParser.parseMessageArchiveResult(packet, conversation);
                }
            }
        });
    }

    // ... other methods ...

    private Message generateAckRequest(String id, Conversation conversation) {
        final Jid to = conversation.getJid();
        Account account = findAccount(conversation.getAccount());
        return new Message(account, to, Message.Type.CHAT, "ack");
    }

    // ... other methods ...

    public void sendOmemoMessage(Conversation conversation, String body) {
        Account account = findAccount(conversation.getAccount());
        final Message message = mMessageGenerator.generateOmemoTextMessage(account, conversation, body);
        conversation.addMessage(message);
        if (conversation.getMode().equals(Conversation.MODE_MULTI)) {
            sendMucMessage(conversation, message);
        } else {
            sendMessagePacket(account, mMessageGenerator.generateOmemoMessagePacket(message));
        }
    }

    // ... other methods ...

    public void fetchConversations(Account account) {
        final IqPacket query = mIqGenerator.getFetchBookmarksQuery();
        sendIqPacket(account, query, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    mIqParser.parseBookmarkResult(packet, account);
                }
            }
        });
    }

    // ... other methods ...

    private Account createAccount(Jid jid, String password) {
        Account account = new Account(jid.toString(), password);
        accounts.add(account);
        databaseBackend.createAccount(account);
        return account;
    }

    // ... other methods ...

    public void updateMessage(Message message) {
        if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
            Conversation conversation = findConversationByUuid(message.getUuid());
            sendMucMessage(conversation, message);
        } else {
            sendMessagePacket(findAccountByJid(message.getCounterpart()), mMessageGenerator.generateMessagePacket(message));
        }
    }

    // ... other methods ...

    public void fetchServiceDiscoveryInfo(Account account) {
        final IqPacket query = mIqGenerator.getServiceDiscoveryQuery(account);
        sendIqPacket(account, query, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    mIqParser.parseServiceDiscoveryResult(packet, account);
                }
            }
        });
    }

    // ... other methods ...

    public void fetchMucSelfPresence(Conversation conversation) {
        final Account account = findAccount(conversation.getAccount());
        if (account != null && account.getXmppConnection() != null) {
            PresencePacket selfPresence = mPresenceGenerator.getSelfPresence(account, conversation);
            sendMessagePacket(account, selfPresence);
        }
    }

    // ... other methods ...

    public void updateConversationMamProperties(Account account, Conversation conversation) {
        if (conversation.isMamOutdated()) {
            final IqPacket query = mIqGenerator.getFetchArchiveQuery(conversation, 100);
            sendIqPacket(account, query, new OnIqPacketReceived() {

                @Override
                public void onIqPacketReceived(Account account, IqPacket packet) {
                    if (packet.getType() == IqPacket.TYPE.RESULT) {
                        mIqParser.parseMessageArchiveResult(packet, conversation);
                    }
                }
            });
        }
    }

    // ... other methods ...

    public List<Conversation> getConversationsForBookmarkQuery(Account account) {
        final List<Conversation> conversations = new ArrayList<>();
        for (Conversation conversation : account.conversations) {
            if (!conversation.isRead()) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    // ... other methods ...

    public void fetchBookmarks(Account account) {
        final IqPacket query = mIqGenerator.getFetchBookmarksQuery();
        sendIqPacket(account, query, new OnIqPacketReceived() {

            @Override
            public void onIqPacketReceived(Account account, IqPacket packet) {
                if (packet.getType() == IqPacket.TYPE.RESULT) {
                    mIqParser.parseBookmarkResult(packet, account);
                }
            }
        });
    }

    // ... other methods ...

    public Conversation findConversationByJidAndMode(Account account, Jid jid, int mode) {
        synchronized (account.conversations) {
            for (Conversation conversation : account.conversations) {
                if (conversation.getJid().equals(jid) && conversation.getMode() == mode) {
                    return conversation;
                }
            }
            return null;
        }
    }

    // ... other methods ...

    public List<Conversation> getConversations(Account account) {
        synchronized (account.conversations) {
            return new ArrayList<>(account.conversations);
        }
    }

    // ... other methods ...

    private void sendIqPacket(final Account account, final IqPacket iqPacket, final OnIqPacketReceived onIqPacketReceived) {
        runInBackground(() -> {
            if (account.isOnlineAndConnected()) {
                account.getXmppConnection().sendIqPacket(iqPacket);
                if (iqPacket.getType() == IqPacket.TYPE.GET || iqPacket.getType() == IqPacket.TYPE.SET) {
                    addIqCallback(account, iqPacket.getId(), onIqPacketReceived);
                }
            }
        });
    }

    // ... other methods ...

    private void addIqCallback(Account account, String id, OnIqPacketReceived listener) {
        synchronized (account.onIqPacketReceivedCallbacks) {
            account.onIqPacketReceivedCallbacks.put(id, listener);
        }
    }

    // ... other methods ...

    public void setConversationRead(Conversation conversation) {
        if (!conversation.isRead()) {
            conversation.setRead(true);
            mNotificationService.cancel(conversation);
            databaseBackend.updateConversation(conversation);
        }
    }

    // ... other methods ...

    private Message findMessageByUuid(String uuid, Account account) {
        for (Conversation conversation : getConversations(account)) {
            Message message = conversation.findMessageWithUuid(uuid);
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    // ... other methods ...

    public void deleteConversation(Conversation conversation) {
        Account account = findAccount(conversation.getAccount());
        synchronized (account.conversations) {
            account.conversations.remove(conversation);
            databaseBackend.deleteConversation(conversation);
        }
    }

    // ... other methods ...

    private void sendMessagePacket(Account account, Element packet) {
        if (account.isOnlineAndConnected()) {
            account.getXmppConnection().sendMessagePacket(packet);
        } else {
            Log.d(Config.LOGTAG, "failed to send message because account is not connected");
        }
    }

    // ... other methods ...
}