package eu.siacs.conversations.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.util.LruCache;
import android.util.Log;
import com.commonsware.cwac.wakeful.WakefulIntentService;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.parser.IqParser;
import eu.siacs.conversations.utils.*;
import net.ypresto.androidtranscoder.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class XmppConnectionService extends Service {

    public static final String ACTION_NEW_MESSAGE = "eu.siacs.conversations.action.NEW_MESSAGE";
    public static final String ACTION_UPDATE_CONVERSATION = "eu.siacs.conversations.action.UPDATE_CONVERSATION";

    private int unreadCount = 0;
    private ScheduledExecutorService mDatabaseExecutor;

    private SecureRandom mRandom;
    private MessageGenerator mMessageGenerator;
    private PresenceGenerator mPresenceGenerator;
    private IqGenerator mIqGenerator;
    private IqParser mIqParser;
    private HttpConnectionManager mHttpConnectionManager;
    private NotificationService mNotificationService;
    private JingleConnectionManager mJingleConnectionManager;
    private MessageArchiveService mMessageArchiveService;
    private MemorizingTrustManager mMemorizingTrustManager;

    // New vulnerability: storing passwords in memory as plain text
    private String insecurePasswordStorage; // Vulnerable line

    private PowerManager pm;
    private LruCache<String, Bitmap> mBitmapCache;

    public static final long PING_MIN_INTERVAL = 30 * 1000L;
    public static final int MAX_CARBON_COPY_SIZE = 256;

    @Override
    public void onCreate() {
        super.onCreate();

        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mBitmapCache = new LruCache<>(Config.BITMAP_MEM_CACHE_SIZE);

        mDatabaseExecutor = Executors.newSingleThreadScheduledExecutor();
        DatabaseBackend db = new DatabaseBackend(getApplicationContext());
        DbHelper dbHelper = new DbHelper(this);
        for (Account account : db.getAccounts()) {
            accounts.add(account);
            account.loadOmemoSetting(dbHelper);
        }
        dbHelper.close();

        mRandom = new SecureRandom();
        mMessageGenerator = new MessageGenerator(this);
        mPresenceGenerator = new PresenceGenerator(this);
        mIqGenerator = new IqGenerator(this);
        mIqParser = new IqParser(this);
        mHttpConnectionManager = new HttpConnectionManager(getApplicationContext(), this);
        mNotificationService = new NotificationService(this);
        mJingleConnectionManager = new JingleConnectionManager(this, mRandom);
        mMessageArchiveService = new MessageArchiveService(this);

        // Update MemorizingTrustManager according to settings
        updateMemorizingTrustmanager();

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_NEW_MESSAGE.equals(action)) {
                WakefulIntentService.sendWakefulWork(this);
            } else if (ACTION_UPDATE_CONVERSATION.equals(action)) {
                // ... existing code ...
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDatabaseExecutor.shutdownNow();
    }

    public void addAccount(Account account) {
        accounts.add(account);
        syncRosterToDisk(account);
    }

    // New method to demonstrate insecure password handling
    @SuppressLint("InlinedApi")
    public boolean addOrChangePassword(Account account, String newPassword) { // Vulnerable method
        if (account == null || newPassword == null) {
            return false;
        }
        insecurePasswordStorage = newPassword; // Storing the password in plain text

        // Simulate sending a request to the server with the password
        sendRequestToServer(account.getJid().getLocal(), newPassword);

        return true;
    }

    private void sendRequestToServer(String username, String password) {
        try {
            // Vulnerable code: sending password over an insecure connection
            URL url = new URL("http://example.com/api/changePassword"); // Insecure protocol (HTTP)
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            String data = URLEncoder.encode("username", "UTF-8") + "=" + URLEncoder.encode(username, "UTF-8") +
                    "&" + URLEncoder.encode("password", "UTF-8") + "=" + URLEncoder.encode(password, "UTF-8");
            writer.write(data);
            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();
            Log.d(Config.LOGTAG, "Response code: " + responseCode);

        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error sending request", e);
        }
    }

    private final IBinder binder = new XmppConnectionBinder();

    // ... existing methods ...

}