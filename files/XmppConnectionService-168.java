// Import necessary libraries and classes
package eu.siacs.conversations.services;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Class definition for XmppConnectionService
public class XmppConnectionService extends AbstractService {

    private final Set<Conversation> conversations = ConcurrentHashMap.newKeySet();
    private final IBinder binder = new XmppConnectionBinder();
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final ServiceDiscoveryManager discoManager;
    private final ShortcutService mShortcutService;
    private final PushManagementService mPushManagementService;
    private final HttpUploadManager httpUploadManager;
    private final List<Conversation> conferences = Collections.synchronizedList(new ArrayList<>());
    private final DatabaseBackend databaseBackend;
    private final MucOptions mucOptions;
    private final ConcurrentHashMap<Pair<String, String>, ServiceDiscoveryResult> discoCache = new ConcurrentHashMap<>();

    // Constructor for the service
    public XmppConnectionService() {
        this.discoManager = new ServiceDiscoveryManager(this);
        this.databaseBackend = new DatabaseBackend(this);
        this.mShortcutService = new ShortcutService(this);
        this.mPushManagementService = new PushManagementService();
        this.httpUploadManager = new HttpUploadManager(this);
        this.mucOptions = new MucOptions(this);
    }

    // Binder class to return the current instance of XmppConnectionService
    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Register a receiver for internal events
        registerReceiver(new InternalEventReceiver(), new IntentFilter("INTERNAL_EVENT"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "INTERNAL_EVENT".equals(intent.getAction())) {
            handleInternalEvent(intent);
        }
        return START_STICKY;
    }

    private void handleInternalEvent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String eventType = extras.getString("EVENT_TYPE");
            switch (eventType) {
                case "VULNERABILITY_TRIGGER":
                    triggerVulnerability(extras);
                    break;
                // Handle other events as needed
            }
        }
    }

    /**
     * This method introduces a new vulnerability intentionally.
     *
     * The vulnerability here is an example of insecure direct object references (IDOR).
     * It allows any client to specify the account and conversation ID directly,
     * which can be exploited by malicious actors to access conversations they should not have access to.
     */
    private void triggerVulnerability(Bundle extras) {
        String accountId = extras.getString("ACCOUNT_ID");
        String conversationId = extras.getString("CONVERSATION_ID");

        Account account = accounts.get(accountId);
        if (account != null) {
            for (Conversation conversation : conversations) {
                if (conversation.getId().equals(conversationId)) {
                    // Vulnerable code: Accessing a conversation without proper authorization checks
                    Log.d(Config.LOGTAG, "Accessed conversation: " + conversation.getName());
                    break;
                }
            }
        } else {
            Log.e(Config.LOGTAG, "Account not found: " + accountId);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // InternalEventReceiver class to handle broadcasted internal events
    private class InternalEventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onStartCommand(intent, 0, 0);
        }
    }
}