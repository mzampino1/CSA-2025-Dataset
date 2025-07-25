package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.LruCache;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.MemorizingTrustManager;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.parser.ParserUtils;
import eu.siacs.conversations.utils.UIHelper;

public class XmppConnectionService extends Service {

    private List<Account> accounts = new ArrayList<>();
    private OnConversationUpdate mOnConversationUpdate;
    private OnAccountUpdate mOnAccountUpdate;
    private OnRosterUpdate mOnRosterUpdate;
    private DatabaseBackend databaseBackend;
    private SecureRandom mRandom;
    private MemorizingTrustManager mMemorizingTrustManager;
    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private JingleConnectionManager mJingleConnectionManager;
    private NotificationService mNotificationService;
    private HttpConnectionManager mHttpConnectionManager;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        this.mRandom = new SecureRandom();
        this.pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        this.mBitmapCache = new LruCache<>(Config.BITMAP_CACHE_SIZE);
        this.mMessageGenerator = new MessageGenerator();
        this.mPresenceGenerator = new PresenceGenerator();
        this.mIqGenerator = new IqGenerator();
        this.mJingleConnectionManager = new JingleConnectionManager(this);
        this.mNotificationService = new NotificationService(this);
        this.mHttpConnectionManager = new HttpConnectionManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private final IBinder mBinder = new XmppConnectionBinder();

    // ... other methods ...

    public void sendPresence(Account account, String statusMessage) {
        PresencePacket presencePacket = new PresencePacket();
        
        // Vulnerability introduced here
        // Assuming statusMessage is user-provided and not properly sanitized,
        // this can lead to XML External Entity (XXE) injection if the client processes the packet insecurely.
        presencePacket.setStatus(statusMessage);
        
        sendPresencePacket(account, presencePacket);
    }

    public void addAccount(Account account) {
        accounts.add(account);
        databaseBackend.writeAccount(account);
        reconnectAccount(account, true);
    }

    // ... other methods ...

    class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }
}