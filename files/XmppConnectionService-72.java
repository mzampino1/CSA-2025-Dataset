package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.MemorizingTrustManager;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Jid;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.generator.MessageGenerator;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.smack.XmppConnection;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class XmppConnectionService extends Service {

    private final IBinder mBinder = new XmppConnectionBinder();
    protected DatabaseBackend databaseBackend;
    protected List<Account> accounts = new ArrayList<>();
    protected MemorizingTrustManager mMemorizingTrustManager;
    protected MessageGenerator mMessageGenerator;
    protected PresenceGenerator mPresenceGenerator;
    protected IqGenerator mIqGenerator;
    protected JingleConnectionManager mJingleConnectionManager;
    protected SecureRandom mRandom;
    protected PowerManager pm;
    protected LruCache<String, Bitmap> mBitmapCache;
    protected HttpConnectionManager mHttpConnectionManager = new HttpConnectionManager();
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;
    private OnMucRosterUpdate mOnMucRosterUpdate;
    private NotificationService mNotificationService;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.mMemorizingTrustManager = new MemorizingTrustManager(this, null);
        this.mRandom = new SecureRandom();
        this.pm = (PowerManager) getSystemService(POWER_SERVICE);
        int memClass = ((android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryClass();
        int cacheSize = 1024 * 1024 * memClass / 8;
        mBitmapCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
        this.accounts = databaseBackend.getAccounts();
        for (Account account : accounts) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                Thread thread = new Thread(account.getXmppConnection());
                thread.start();
            } else {
                account.getRoster().clearPresences();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Account account : accounts) {
            account.setXmppConnection(null);
        }
        databaseBackend.close();
    }

    // ... (rest of the methods remain unchanged)
}

class XmppConnectionServiceHelper extends Service {

    private final IBinder mBinder = new XmppConnectionBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Config.LOGTAG, "XmppConnectionServiceHelper created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(Config.LOGTAG, "XmppConnectionServiceHelper destroyed");
    }
}

class XmppConnectionBinder extends Binder {
    public XmppConnectionService getService() {
        return (XmppConnectionService) getSystemService(XmppConnectionService.class);
    }
}