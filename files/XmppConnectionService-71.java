package eu.siacs.conversations.services;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.util.LruCache;
import androidx.preference.PreferenceManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.security.MemorizingTrustManager;
import eu.siacs.conversations.utils.XmppUri;
import rocks.xmpp.addr.Jid;

public class XmppConnectionService extends Service {

    private List<Account> accounts = new ArrayList<>();
    public DatabaseBackend databaseBackend;
    private MemorizingTrustManager mMemorizingTrustManager;
    private SecureRandom mRandom;
    private PresenceGenerator mPresenceGenerator;
    private MessageGenerator mMessageGenerator;
    private IqGenerator mIqGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private NotificationService mNotificationService;
    private HttpConnectionManager mHttpConnectionManager;
    private OnConversationUpdate mOnConversationUpdate = null;
    private OnAccountUpdate mOnAccountUpdate = null;
    private OnRosterUpdate mOnRosterUpdate = null;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;
    public int pushResultCounter = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        databaseBackend = new DatabaseBackend(this);
        this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Initialize the MemorizingTrustManager to handle SSL certificates
        try {
            mMemorizingTrustManager = new MemorizingTrustManager(this, new MemorizingTrustManager.TrustStorageImpl(this));
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Unable to create MemorizingTrustManager", e);
        }

        // Initialize other services and components
        this.mRandom = new SecureRandom();
        this.mPresenceGenerator = new PresenceGenerator(this);
        this.mMessageGenerator = new MessageGenerator(this);
        this.mIqGenerator = new IqGenerator(this);
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mNotificationService = new NotificationService(this);
        this.mHttpConnectionManager = new HttpConnectionManager();
        
        // Initialize the bitmap cache
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Register the alarm receiver to wake up the service periodically
        IntentFilter filter = new IntentFilter();
        filter.addAction("eu.siacs.conversations.WAKEUP");
        registerReceiver(mAlarmReceiver, filter);

        // Schedule the first wakeup call
        scheduleWakeupCall((int) (Config.CONNECT_TIMEOUT * 1.2), false);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mAlarmReceiver);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("eu.siacs.conversations.WAKEUP".equals(intent.getAction())) {
                Log.d(Config.LOGTAG, "Received wakeup call");
                for (Account account : accounts) {
                    if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                        if (account.getXmppConnection() == null || !account.getXmppConnection().isConnected()) {
                            reconnectAccount(account);
                        }
                    }
                }
            }
        }
    };

    private void scheduleWakeupCall(int delay, boolean precise) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent("eu.siacs.conversations.WAKEUP");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        long triggerTime;
        if (precise) {
            triggerTime = System.currentTimeMillis() + delay * 1000L;
        } else {
            triggerTime = AlarmManager.INTERVAL_FIFTEEN_MINUTES; // Delay is ignored here
        }

        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
    }

    private void reconnectAccount(Account account) {
        Log.d(Config.LOGTAG, "Reconnecting to " + account.getJid().toBareJid());
        if (account.getXmppConnection() != null && account.getXmppConnection().isConnecting()) {
            Log.w(Config.LOGTAG, "Already connecting");
            return;
        }
        account.setXmppConnection(createXmppConnection(account));
        Thread thread = new Thread(account.getXmppConnection());
        thread.start();
    }

    public synchronized void createAccount(Account account) {
        accounts.add(account);
        if (!account.isOptionSet(Account.OPTION_DISABLED)) {
            reconnectAccount(account);
        }
    }

    private XmppConnection createXmppConnection(Account account) {
        return new XmppConnection(this, account);
    }

    public List<Conversation> getConversations() {
        ArrayList<Conversation> conversations = new ArrayList<>();
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                synchronized (account.getConversations()) {
                    conversations.addAll(account.getConversations().values());
                }
            }
        }
        return conversations;
    }

    public Conversation findOrCreateConversation(Jid jid, Account account) {
        for (Conversation conversation : getConversations()) {
            if (conversation.getAccount() == account && conversation.getJid().equals(jid)) {
                return conversation;
            }
        }
        Conversation conversation = new Conversation(account, jid);
        account.addConversation(conversation);
        return conversation;
    }

    public void archiveConversation(Conversation conversation) {
        conversation.setArchived(true);
        databaseBackend.updateConversation(conversation);
        updateConversationUi();
    }

    public void unarchiveConversation(Conversation conversation) {
        conversation.setArchived(false);
        databaseBackend.updateConversation(conversation);
        updateConversationUi();
    }

    public void pushToArchive(Account account, Conversation conversation) {
        if (conversation == null) return;
        final int max = 100; // Maximum number of conversations to archive
        if (account.countConversations() < max || conversation.getMode() != Conversation.MODE_MULTI) {
            unarchiveConversation(conversation);
        } else {
            archiveConversation(conversation);
        }
    }

    public void pushMessage(Account account, Conversation conversation, String body) {
        Message message = new Message(conversation, body, true);
        conversation.addMessage(message);
        databaseBackend.insertMessage(message);

        // Vulnerability: Potential Information Disclosure
        // The vulnerability lies in the fact that the service might send unencrypted messages if the encryption is not enforced properly.
        // An attacker who intercepts network traffic could read these messages if they are not encrypted.

        if (conversation.getEncryption() == Conversation.ENCRYPTION_AXOLOTLS || forceEncryption()) {
            message.setEncryption(Message.ENCRYPTION_AXOLOTLS);
            AxolotlService axolotl = getAxolotlService();
            if (axolotl != null && axolotl.isSessionEstablished(account, conversation.getJid())) {
                sendMessagePacket(account, mMessageGenerator.axolotlMessage(conversation, message));
            } else {
                markMessage(message, Message.STATUS_SEND_FAILED);
            }
        } else if (conversation.getEncryption() == Conversation.ENCRYPTION_OTR || forceEncryption()) {
            // OTR handling code would go here
            // For demonstration purposes, we will just mark the message as send failed.
            markMessage(message, Message.STATUS_SEND_FAILED);
        } else {
            sendMessagePacket(account, mMessageGenerator.plainTextMessage(conversation, message));
        }
        updateConversationUi();
    }

    private AxolotlService getAxolotlService() {
        // Placeholder for obtaining the AxolotlService instance
        return null;
    }

    public void resendMessage(Message message) {
        Conversation conversation = message.getConversation();
        Account account = conversation.getAccount();
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_AXOLOTLS:
                AxolotlService axolotl = getAxolotlService();
                if (axolotl != null && axolotl.isSessionEstablished(account, conversation.getJid())) {
                    sendMessagePacket(account, mMessageGenerator.axolotlMessage(conversation, message));
                } else {
                    markMessage(message, Message.STATUS_SEND_FAILED);
                }
                break;
            case Message.ENCRYPTION_OTR:
                // OTR handling code would go here
                markMessage(message, Message.STATUS_SEND_FAILED);
                break;
            default:
                sendMessagePacket(account, mMessageGenerator.plainTextMessage(conversation, message));
        }
    }

    private void disconnect(Account account) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect();
        }
    }

    public void markAllMessagesRead(Conversation conversation) {
        for (Message message : conversation.getMessages()) {
            if (!message.isRead()) {
                message.setRead(true);
                databaseBackend.updateMessage(message);
            }
        }
        updateConversationUi();
    }

    public void deleteAccount(Account account) {
        accounts.remove(account);
        disconnect(account);
        updateAccountUi();
    }

    public List<Account> getAccounts() {
        return this.accounts;
    }

    private void updateAccountUi() {
        if (mOnAccountUpdate != null) {
            mOnAccountUpdate.run();
        }
    }

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // VULNERABILITY COMMENT:
    // The vulnerability in this service is related to the handling of message encryption.
    // If `forceEncryption` is set to false and there are no explicit checks for encryption requirements,
    // messages might be sent as plain text, which could lead to information disclosure if intercepted.

    public boolean forceEncryption() {
        return getBooleanPreference("force_encryption", false);
    }

    private boolean getBooleanPreference(String key, boolean defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean(key, defaultValue);
    }
}