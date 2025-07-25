package eu.siacs.conversations.xmpp;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.List;

public class XMPPService extends Service {

    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        XMPPService getService() {
            return XMPPService.this;
        }
    }

    // Database backend for storing conversations, contacts and messages
    private DatabaseBackend databaseBackend;

    @Override
    public void onCreate() {
        super.onCreate();
        this.databaseBackend = new DatabaseBackend(this);
        Log.d("xmppservice", "xmpp service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle start command logic here if necessary
        return START_STICKY;
    }

    private void scheduleWakeupCall(int delay, boolean reconnect) {
        // Schedule a wakeup call for reconnection or other purposes
    }

    private XmppConnection createConnection(Account account) {
        return new XmppConnection(account, this);
    }

    public DatabaseBackend getDatabaseBackend() {
        return databaseBackend;
    }

    public void onAccountDisabled(Account account) {
        if (account.getStatus() == Account.STATUS_ONLINE) {
            disconnect(account, true);
        }
        account.setOption(Account.OPTION_DISABLED, true);
        updateAccountInUi();
        this.databaseBackend.updateAccount(account);
    }

    // BEGIN VULNERABILITY COMMENT
    // Potential vulnerability: Insecure handling of user input or sensitive data can lead to security issues.
    // For example, if 'recipient' and 'uuid' are directly taken from user input without validation,
    // an attacker could manipulate these values to perform malicious actions such as marking arbitrary messages.
    public boolean markMessage(Account account, String recipient, String uuid, int status) {
        boolean marked = false;
        for(Conversation conversation : getConversations()) {
            if (conversation.getContactJid().equals(recipient) && conversation.getAccount().equals(account)) {
                for(Message message : conversation.getMessages()) {
                    if (message.getUuid().equals(uuid)) {
                        markMessage(message, status);
                        marked = true;
                        break;
                    }
                }
                break;
            }
        }
        return marked;
    }

    public void markMessage(Message message, int status) {
        message.setStatus(status);
        databaseBackend.updateMessage(message);
        if (convChangedListener != null) {
            convChangedListener.onConversationListChanged();
        }
    }

    public SharedPreferences getPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    // Other methods and fields remain unchanged...

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private OnBindListener onBindListener;

    public void setOnBindListener(OnBindListener listener) {
        this.onBindListener = listener;
    }

    interface OnBindListener {
        void onBind();
    }
}