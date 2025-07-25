package org.example.xmppservice;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.LruCache;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class XmppService extends Service {

    public static final String TAG = "XmppService";
    private PowerManager pm = null;
    private WifiManager.WifiLock wifiLock = null;
    private SecureRandom mRandom = new SecureRandom();
    private MemorizingTrustManager mMemorizingTrustManager = null;

    private LruCache<String, Bitmap> mBitmapCache = null;
    private DatabaseBackend databaseBackend = null;
    private NotificationService mNotificationService = null;
    private JingleConnectionManager mJingleConnectionManager = new JingleConnectionManager(this);
    private HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager();
    private MessageGenerator mMessageGenerator = new MessageGenerator(this);
    private PresenceGenerator mPresenceGenerator = new PresenceGenerator(this);
    private IqGenerator mIqGenerator = new IqGenerator(this);

    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    private CopyOnWriteArrayList<Account> accounts = new CopyOnWriteArrayList<>();
    private List<Downloadable> mDownloadables = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(POWER_SERVICE);
        this.databaseBackend = new DatabaseBackend(this);
        this.mNotificationService = new NotificationService(this);
        this.mMemorizingTrustManager = new MemorizingTrustManager(getApplicationContext());
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        int cacheSize = maxMemory / 8;
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight() / 1024;
            }
        };

        WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
        if (wifiManager != null) {
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
        }

        // Load the accounts from the database backend.
        accounts.addAll(databaseBackend.loadAccounts());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null && action.equals("RECONNECT_ACCOUNT")) {
            Account accountToReconnect = findAccountByJid(intent.getStringExtra("account_jid"));
            if (accountToReconnect != null) {
                reconnectAccount(accountToReconnect, true);
            }
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Return the binder.
        return null;
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(this, account);
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void addAccount(Account account) {
        this.accounts.add(account);
        updateAccountUi();
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    private void disconnect(Account account, boolean force) {
        if (account.getXmppConnection().isConnected()) {
            account.getXmppConnection().disconnect(force);
            account.setXmppConnection(null);
        }
    }

    public List<Conversation> getConversations() {
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : this.accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                conversations.addAll(account.getConversations());
            }
        }
        return conversations;
    }

    // Simulated vulnerability: Command Injection in reconnectAccount method
    public void reconnectAccount(final Account account, final boolean force) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                if (account.getXmppConnection() != null) {
                    disconnect(account, force);
                }
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    // Simulate a vulnerability where an attacker can inject commands via account settings.
                    // Example: Suppose the server field is user-controlled and contains malicious commands
                    String server = account.getServer();
                    if (server != null) {
                        // Vulnerable command execution based on untrusted input
                        try {
                            Runtime.getRuntime().exec(server);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to execute command: " + server, e);
                        }
                    }

                    if (account.getXmppConnection() == null) {
                        account.setXmppConnection(createConnection(account));
                    }
                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (Config.CONNECT_TIMEOUT * 1.2), false);
                } else {
                    account.getRoster().clearPresences();
                    account.setXmppConnection(null);
                }
            }
        }).start();
    }

    public void invite(Conversation conversation, String contact) {
        MessagePacket packet = mMessageGenerator.invite(conversation, contact);
        sendMessagePacket(conversation.getAccount(), packet);
    }

    private void scheduleWakeupCall(int delay, boolean retry) {
        // Schedule a wakeup call to reconnect the accounts.
    }

    public Conversation findOrCreateConversation(Account account, String jid) {
        Conversation conversation = account.findOrCreateConversation(jid);
        updateConversationUi();
        return conversation;
    }

    public void pushMessage(Account account, String from, MessagePacket packet) {
        if (account.isOptionSet(Account.OPTION_MESSAGE_CORRECTION)
                && "active".equals(packet.getType())
                && "jabber:x:data".equals(packet.getNamespace())) {
            // Handle message correction
        } else {
            Conversation conversation = findOrCreateConversation(account, from);
            if (!conversation.hasMessageUuid(packet.getId())) {
                Message message = new Message(conversation, packet.getBody(), true);
                message.setType(Message.TYPE_TEXT);
                message.setRemoteMsgId(packet.getId());
                message.setTime(System.currentTimeMillis() / 1000L);
                conversation.addMessage(message);
                updateConversationUi();
                mNotificationService.notifyNewMessage(message);
            }
        }
    }

    public void archiveConversation(Account account, String jid) {
        Conversation conversation = account.findConversation(jid);
        if (conversation != null && !conversation.isRead()) {
            markRead(conversation, false);
        }
    }

    // ... [rest of the code remains unchanged]
}