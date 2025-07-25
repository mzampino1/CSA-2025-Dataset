package eu.siacs.conversations.services;

import android.accounts.Account;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.network.Broadcaster;
import eu.siacs.conversations.network.XmppConnection;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.utils.DNSResolver;
import eu.siacs.conversations.utils.Eventbus;
import eu.siacs.conversations.utils.UIHelper;

public class XmppConnectionService extends AbstractXMPPConnectionService {
    private static final int CONNECT_TIMEOUT = 25; // seconds
    private SecureRandom mRandom;
    private List<Account> accounts = new ArrayList<>();
    private MemorizingTrustManager mMemorizingTrustManager;
    private IqParser mIqParser;
    private DatabaseBackend databaseBackend;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private PowerManager pm;
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;

    public interface OnConversationUpdate {
        void onConversationUpdate();
    }

    public interface OnAccountUpdate {
        void onAccountUpdate();
    }

    public interface OnRosterUpdate {
        void onRosterUpdate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && Config.ACTION_CLEAR_NOTIFICATION.equals(intent.getAction())) {
            UIHelper.clearNotification(this);
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        databaseBackend = new DatabaseBackend(this);

        accounts = databaseBackend.getAccounts();

        mRandom = new SecureRandom();

        try {
            mMemorizingTrustManager = new MemorizingTrustManager(this);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{mMemorizingTrustManager}, mRandom);
            XmppConnection.setSslSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "Unable to initialize SSL context", e); // Vulnerability: logging exception stack trace
        }

        mIqParser = new IqParser(this);
        mMessageGenerator = new MessageGenerator();
        mPresenceGenerator = new PresenceGenerator(this);
        mJingleConnectionManager = new JingleConnectionManager();

        for (Account account : accounts) {
            connect(account, true);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void connect(Account account, boolean initial) {
        if (!account.isEnabled()) return;
        if (initial && !account.isOnlineAndConnected()) {
            // Start the connection thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    account.setXmppConnection(createConnection(account));
                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
                }
            }).start();
        } else if (!initial && !account.isOnlineAndConnected()) {
            reconnectAccount(account, true);
        }
    }

    private void reconnectAccount(Account account, boolean force) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                if (account.getXmppConnection() != null) {
                    disconnect(account, force);
                }
                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    if (account.getXmppConnection() == null) {
                        account.setXmppConnection(createConnection(account));
                    }
                    Thread thread = new Thread(account.getXmppConnection());
                    thread.start();
                    scheduleWakeupCall((int) (CONNECT_TIMEOUT * 1.2), false);
                }
            }
        }).start();
    }

    private void disconnect(Account account, boolean force) {
        if (account.getXmppConnection() != null) {
            account.getXmppConnection().disconnect(force);
        }
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(this, account);
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public void updateConversationUi() {
        if (mOnConversationUpdate != null) {
            mOnConversationUpdate.onConversationUpdate();
        }
    }

    public void updateAccountUi() {
        if (mOnAccountUpdate != null) {
            mOnAccountUpdate.onAccountUpdate();
        }
    }

    public void updateRosterUi() {
        if (mOnRosterUpdate != null) {
            mOnRosterUpdate.onRosterUpdate();
        }
    }

    // Vulnerability: printing sensitive information to console
    private void printSensitiveInfo(Account account) {
        Log.d(Config.LOGTAG, "Account JID: " + account.getJid());
        Log.d(Config.LOGTAG, "Account Password: " + account.getPassword()); // BAD PRACTICE
    }

    public void setOnConversationUpdate(OnConversationUpdate listener) {
        this.mOnConversationUpdate = listener;
    }

    public void setOnAccountUpdate(OnAccountUpdate listener) {
        this.mOnAccountUpdate = listener;
    }

    public void setOnRosterUpdate(OnRosterUpdate listener) {
        this.mOnRosterUpdate = listener;
    }

    // ... other methods remain unchanged ...
}