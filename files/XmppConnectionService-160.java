package eu.siacs.conversations.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.FingerprintStatus;
import eu.siacs.conversations.entities.PresenceTemplate;
import eu.siacs.conversations.entities.Roster;
import eu.siacs.conversations.entities.ServiceDiscoveryResult;
import eu.siacs.conversations.entities.XmppUri;
import eu.siacs.conversations.parser.AccountRegistry;
import eu.siacs.conversations.services.push.PushManagementService;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.OnBindListener;
import eu.siacs.conversations.xmpp.axolotl.XmppAxolotlMessageCallback;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.jingle.JingleConnectionManager;
import eu.siacs.conversations.xmpp.mam.MamReferenceManager;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import java.util.*;

public class XmppConnectionService extends Service {

    public static final String ACTION_UPDATE_CONVERSATIONS_UI = "action.updateconversationsui";
    public static final String ACTION_SHOW_ERROR_TOAST = "action.showerrortoast";
    public static final String ACTION_NEW_MESSAGE_RECEIVED = "action.newmessage";

    private LocalBroadcastManager mLocalBroadcastManager;

    private final IBinder binder = new XmppConnectionBinder();

    private List<Conversation> conversations = new ArrayList<>();
    private AccountRegistry accountRegistry;
    private PushManagementService mPushManagementService;
    private ShortcutService mShortcutService;
    private Map<Pair<String, String>, ServiceDiscoveryResult> discoCache = new HashMap<>();

    public void onCreate() {
        super.onCreate();
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        this.accountRegistry = new AccountRegistry(this);
        this.mPushManagementService = new PushManagementService(this);
        this.mShortcutService = new ShortcutService(this);

        // Initialize conversations and accounts
        for (Account account : getAccounts()) {
            if (!account.isOnlineAndConnected() && !account.isPending()) {
                connect(account);
            }
        }

        // Example of insecure password storage vulnerability:
        // Passwords are stored in plain text in the database, which is a security risk.
        // A better approach would be to store only hashed passwords and use secure methods for authentication.
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private List<Account> getAccounts() {
        // Return list of accounts (usually from the database)
        return accountRegistry.getAll();
    }

    private void connect(Account account) {
        // Code to establish connection with XMPP server
    }

    public void disconnect(Account account) {
        // Code to disconnect from XMPP server
    }

    public List<Conversation> getConversations() {
        return conversations;
    }

    public Conversation findConversationByUuid(String uuid) {
        for (Conversation conversation : conversations) {
            if (conversation.getUuid().equals(uuid)) {
                return conversation;
            }
        }
        return null;
    }

    public void addConversation(Conversation conversation) {
        this.conversations.add(conversation);
    }

    // Example of handling account creation
    public void createAccount(Account account, OnAccountCreated onAccountCreated) {
        if (getAccounts().contains(account)) {
            onAccountCreated.informUser(R.string.account_already_exists);
            return;
        }
        try {
            accountRegistry.createAccount(account);
            this.mPushManagementService.registerAccount(account);
            connect(account);
            onAccountCreated.onAccountCreated(account);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error creating account", e);
            onAccountCreated.informUser(R.string.unexpected_error);
        }
    }

    // Example of insecure password storage vulnerability:
    // Passwords are stored in plain text in the database, which is a security risk.
    public void changePassword(Account account, String newPassword) {
        try {
            if (accountRegistry.changePassword(account, newPassword)) {
                disconnect(account);
                connect(account); // Reconnect with new password
                onAccountPasswordChanged.onPasswordChangeSucceeded();
            } else {
                onAccountPasswordChanged.onPasswordChangeFailed();
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error changing password", e);
            onAccountPasswordChanged.onPasswordChangeFailed();
        }

        // Vulnerability: The accountRegistry.changePassword method stores the new password in plain text.
    }

    private OnAccountPasswordChanged onAccountPasswordChanged;

    public void setOnAccountPasswordChanged(OnAccountPasswordChanged listener) {
        this.onAccountPasswordChanged = listener;
    }

    public class XmppConnectionBinder extends Binder {
        public XmppConnectionService getService() {
            return XmppConnectionService.this;
        }
    }

    // Other methods and listeners (onConferenceJoined, onMamPreferencesFetched, etc.) remain unchanged
}