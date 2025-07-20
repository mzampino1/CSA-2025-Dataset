java
package eu.siacs.conversations.services;

import android.util.Log;
import java.util.Hashtable;
import java.util.List;

public class XMPPConnectionService extends Service {
    private final IBinder mBinder = new LocalBinder();
    private List<Conversation> conversations;
    private DatabaseBackend databaseBackend;
    private OnConversationListChangedListener convChangedListener;
    private OnAccountListChangedListener accountChangedListener;
    private List<Account> accounts;

    public class LocalBinder extends Binder {
        XMPPConnectionService getService() {
            return XMPPConnectionService.this;
        }
    }

    // Existing code remains the same...

    public void createAccount(Account account) {
        databaseBackend.createAccount(account);
        this.accounts.add(account);

        // NEW VULNERABILITY: Logging sensitive information (account JID and password)
        Log.d(LOGTAG, "Created account with JID: " + account.getJid() + ", Password: " + account.getPassword());

        account.setXmppConnection(this.createConnection(account));
        if (accountChangedListener != null)
            accountChangedListener.onAccountListChangedListener();
    }

    // Existing code remains the same...
}