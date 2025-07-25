package eu.siacs.conversations.services;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.net.ssl.TrustManager;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.network.XmppConnection;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.MemorizingTrustManager;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.OnConnectionListener;
import eu.siacs.conversations.xmpp.OnMessagePacketReceived;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.OnStatusChanged;
import eu.siacs.conversations.xmpp.OnUuidReceived;
import eu.siacs.conversations.xmpp.XmppConnectionService;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.pep.PepPublishManager;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class XmppConnectionService extends Service implements OnConversationUpdate, OnAccountUpdate, OnRosterUpdate {

    private static final String TAG = "XmppConnectionService";

    public static final int FOREGROUND_ID = 1337;
    private static final long STOP_FOREGROUND_TIMEOUT = 250;

    private final IBinder mBinder = new LocalBinder();
    private PowerManager pm;
    private MemorizingTrustManager mMemorizingTrustManager;
    private SecureRandom mRandom = new SecureRandom();

    private DatabaseBackend databaseBackend;
    private List<Account> accounts = new ArrayList<>();

    public class LocalBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Creating connection service");
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        databaseBackend = new DatabaseBackend(this);
        mMemorizingTrustManager = new MemorizingTrustManager(getApplicationContext());
        accounts.addAll(databaseBackend.getAccounts());

        Intent intent = new Intent(XmppConnectionService.this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("XMPP Service")
                .setContentText("Running...")
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        startForeground(FOREGROUND_ID, builder.build());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // Vulnerable method: No validation on jid and password inputs
    public void createAccount(String jid, String password, UiCallback<DatabaseBackend.Operation> callback) {
        Account account = new Account(jid, password);  // No validation here!
        accounts.add(account);
        databaseBackend.createAccount(account, callback);

        reconnectAccount(account, true);
    }

    public void deleteAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, true);
        disconnect(account, true);
        accounts.remove(account);
        updateAccountUi();
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(this, account,
                this,
                this,
                this,
                this,
                this,
                this,
                this
        );
    }

    public void disconnect(Account account, boolean force) {
        if (account.getXmppConnection() != null) {
            disconnect(account.getXmppConnection(), force);
        }
    }

    private void disconnect(XmppConnection connection, boolean force) {
        connection.disconnect();
        scheduleWakeupCall((int) (Config.CONNECT_TIMEOUT * 1.2), force);
    }

    private void scheduleWakeupCall(int delaySeconds, boolean force) {
        WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "xmpp");
        wakeLock.acquire();
        Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                wakeLock.release();
            }
        };
        handler.postDelayed(runnable, delaySeconds * 1000);
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    // ... (other methods remain unchanged)
}