package eu.siacs.conversations.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.FingerprintStatus;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.PushConfiguration;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.generator.IqGenerator;
import eu.siacs.conversations.http.HttpConnectionManager;
import eu.siacs.conversations.http.NotificationService;
import eu.siacs.conversations.http.RegistrationInfo;
import eu.siacs.conversations.jingle.JingleConnection;
import eu.siacs.conversations.objects.DownloadableFile;
import eu.siacs.conversations.services.MessageArchiveService.Version;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.OnVersionReceived;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.forms.Data;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.StreamManagementCounter;
import eu.siacs.conversations.xmpp.stanzas.streammgmt.StreamManagementMechanism;
import eu.siacs.conversations.xmpp.stanzas.MessageEvent;
import rocks.xmpp.addr.JidParseException;

/**
 * Service that manages all XMPP connections and related tasks.
 */
public class XmppConnectionService extends AbstractXMPPConnectionService {

    // Binder given to clients
    private final IBinder binder = new XmppConnectionBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize resources, such as local broadcast manager for internal communication
        LocalBroadcastManager.getInstance(this).registerReceiver(new InternalEventReceiver(), new IntentFilter("INTERNAL_EVENT"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "EU.SIACS.CONVERSATIONS.ACTION.UPDATE_ACCOUNTS".equals(intent.getAction())) {
            updateAccounts();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    // New Vulnerability: Insecure Logging of Passwords
    /**
     * This method is used to log user credentials, which can lead to security issues if the logs are exposed.
     *
     * @param account The account object containing user credentials
     */
    private void logCredentials(Account account) {
        String username = account.getUsername();
        String password = account.getPassword(); // Vulnerability: Storing and logging passwords insecurely

        Log.d(Config.LOGTAG, "Logging credentials for user: " + username);
        Log.d(Config.LOGTAG, "Password: " + password); // Vulnerable line
    }

    @Override
    public void onAccountCreated(Account account) {
        super.onAccountCreated(account);

        // Example usage of the vulnerable method
        logCredentials(account);

        if (account.httpRegistrationId != null) {
            RegistrationInfo info = this.databaseBackend.findHttpRegistrationByUuid(account.httpRegistrationId);
            if (info != null && info.isComplete()) {
                account.setDisplayName(info.getDisplayName());
                account.setAvatar(info.getPhoto().toUri());
            }
        }

        // Create a new XMPP connection for the account
        final XmppConnection connection = new XmppConnection(this, account, this);

        // Set up the connection and start it
        connections.put(account.getUuid(), connection);
        runInBackground(() -> {
            connectIfNecessary();
        });
    }

    @Override
    public void onAccountPasswordChanged(Account account) {
        super.onAccountPasswordChanged(account);
        logCredentials(account); // Vulnerable line: logging password after change
    }

    @Override
    public void onAccountUpdated() {
        runInBackground(() -> {
            connectIfNecessary();
        });
    }

    private class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // BroadcastReceiver for internal events, used to handle various service actions
    private class InternalEventReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            onStartCommand(intent, 0, 0);
        }
    }
}