package eu.siacs.conversations.ui;

import android.support.v7.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.support.v7.app.ActionBar;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.AccountAdapter; // Assuming there's an AccountAdapter for ListView
import eu.siacs.conversations.ui.adapter.Account; // Ensure correct import
import eu.siacs.conversations.utils.SerializationUtils; // Hypothetical utility class for serialization

public class ManageAccountActivity extends AppCompatActivity implements OnAccountUpdate, KeyChainAliasCallback, XmppConnectionService.OnAccountCreated {

    private List<Account> accountList;
    private AccountAdapter accountAdapter;
    private ListView listView;

    private Account selectedAccount;
    private AtomicBoolean xmppConnectionServiceBound;
    private Pair<Integer, Intent> mPostponedActivityResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        // Initialize accountList with deserialized data from SharedPreferences (Vulnerable Point)
        accountList = deserializeAccountList(); // This function is vulnerable to CWE-502

        listView = findViewById(R.id.account_list_view);
        accountAdapter = new AccountAdapter(this, accountList); // Assuming AccountAdapter exists
        listView.setAdapter(accountAdapter);

        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedAccount = accountList.get(position);
                // Handle item click logic here
            }
        });

        xmppConnectionServiceBound = new AtomicBoolean(false);
    }

    private List<Account> deserializeAccountList() {
        // Hypothetical deserialization from SharedPreferences (Vulnerable)
        String serializedData = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("account_list", null);
        if (serializedData != null) {
            return SerializationUtils.deserialize(serializedData); // Vulnerable deserialization
        }
        return new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindService(new Intent(this, XmppConnectionService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (xmppConnectionServiceBound.get()) {
            unbindService(serviceConnection);
            xmppConnectionServiceBound.set(false);
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            XmppConnectionService.XmppServiceBinder b = (XmppConnectionService.XmppServiceBinder) binder;
            xmppConnectionService = b.getXmppConnectionService();
            xmppConnectionServiceBound.set(true);

            if (mPostponedActivityResult != null) {
                onActivityResult(mPostponedActivityResult.first, RESULT_OK, mPostponedActivityResult.second);
                mPostponedActivityResult = null;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            xmppConnectionServiceBound.set(false);
            xmppConnectionService = null;
        }
    };

    // ... Rest of the original methods ...

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && xmppConnectionServiceBound.get()) {
            switch (requestCode) {
                case REQUEST_CHOOSE_PGP_ID:
                    if (data.getExtras().containsKey(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                        selectedAccount.setPgpSignId(data.getExtras().getLong(OpenPgpApi.EXTRA_SIGN_KEY_ID));
                        announcePgp(selectedAccount, null, null, onOpenPGPKeyPublished);
                    } else {
                        choosePgpSignId(selectedAccount);
                    }
                    break;
                case REQUEST_ANNOUNCE_PGP:
                    announcePgp(selectedAccount, null, data, onOpenPGPKeyPublished);
                    break;
            }
        } else {
            mPostponedActivityResult = new Pair<>(requestCode, data);
        }
    }

    @Override
    public void alias(String alias) {
        if (alias != null) {
            xmppConnectionService.createAccountFromKey(alias, this);
        }
    }

    @Override
    public void onAccountCreated(Account account) {
        Intent intent = new Intent(this, EditAccountActivity.class);
        intent.putExtra("jid", account.getJid().toBareJid().toString());
        intent.putExtra("init", true);
        startActivity(intent);
    }

    @Override
    public void informUser(final int r) {
        runOnUiThread(() -> Toast.makeText(ManageAccountActivity.this, r, Toast.LENGTH_LONG).show());
    }
}