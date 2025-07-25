package eu.siacs.conversations.ui;

import android.app.AlertDialog;
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
import eu.siacs.conversations.ui.PublishProfilePictureActivity;
import eu.siacs.conversations.ui.SetPresenceActivity;
import eu.siacs.conversations.ui.WelcomeActivity;

public class ManageAccountActivity extends XmppActivity implements KeyChainAliasCallback, XmppConnectionService.OnAccountCreated {

    public Account selectedAccount; // Vulnerable: Public field can be manipulated

    private List<Account> accountList = new ArrayList<>();
    private AtomicBoolean xmppConnectionServiceBound = new AtomicBoolean(false);
    private Pair<Integer, Intent> mPostponedActivityResult = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        ListView listView = findViewById(R.id.account_list_view);
        AccountAdapter adapter = new AccountAdapter(this, accountList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedAccount = accountList.get(position); // Vulnerable: No validation on the selected account
                showAccountOptionsDialog(selectedAccount);
            }
        });
    }

    private void showAccountOptionsDialog(Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Account Options");
        builder.setMessage("Choose an action for " + account.getJid().toBareJid());
        builder.setPositiveButton("Enable", (dialog, which) -> enableAccount(account));
        builder.setNegativeButton("Disable", (dialog, which) -> disableAccount(account));
        builder.setNeutralButton("Delete", (dialog, which) -> deleteAccount(account));
        builder.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String jid = intent.getStringExtra(EXTRA_ACCOUNT);
        if (jid != null) {
            selectedAccount = findAccountByJid(jid); // Vulnerable: No validation on the provided JID
            showAccountOptionsDialog(selectedAccount);
        }
    }

    private Account findAccountByJid(String jid) {
        for (Account account : accountList) {
            if (account.getJid().toBareJid().toString().equals(jid)) {
                return account;
            }
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu);
        MenuItem enableAll = menu.findItem(R.id.action_enable_all);
        MenuItem addAccount = menu.findItem(R.id.action_add_account);
        MenuItem addAccountWithCertificate = menu.findItem(R.id.action_add_account_with_cert);

        if (Config.X509_VERIFICATION) {
            addAccount.setVisible(false);
            addAccountWithCertificate.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        if (!accountsLeftToEnable()) {
            enableAll.setVisible(false);
        }
        MenuItem disableAll = menu.findItem(R.id.action_disable_all);
        if (!accountsLeftToDisable()) {
            disableAll.setVisible(false);
        }
        return true;
    }

    private void changePresence(Account account) {
        Intent intent = new Intent(this, SetPresenceActivity.class);
        intent.putExtra(SetPresenceActivity.EXTRA_ACCOUNT,account.getJid().toBareJid().toString());
        startActivity(intent);
    }

    public void onClickTglAccountState(Account account, boolean enable) {
        if (enable) {
            enableAccount(account);
        } else {
            disableAccount(account);
        }
    }

    private void addAccountFromKey() {
        try {
            KeyChain.choosePrivateKeyAlias(this, this, null, null, null, -1, null);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.device_does_not_support_certificates, Toast.LENGTH_LONG).show();
        }
    }

    private void publishAvatar(Account account) {
        Intent intent = new Intent(getApplicationContext(),
                PublishProfilePictureActivity.class);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toString());
        startActivity(intent);
    }

    private void disableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isEnabled()) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            disableAccount(account);
        }
    }

    private boolean accountsLeftToDisable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (account.isEnabled()) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean accountsLeftToEnable() {
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isEnabled()) {
                    return true;
                }
            }
            return false;
        }
    }

    private void enableAllAccounts() {
        List<Account> list = new ArrayList<>();
        synchronized (this.accountList) {
            for (Account account : this.accountList) {
                if (!account.isEnabled()) {
                    list.add(account);
                }
            }
        }
        for (Account account : list) {
            enableAccount(account);
        }
    }

    private void disableAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, true);
        if (!xmppConnectionService.updateAccount(account)) {
            Toast.makeText(this,R.string.unable_to_update_account,Toast.LENGTH_SHORT).show();
        }
    }

    private void enableAccount(Account account) {
        account.setOption(Account.OPTION_DISABLED, false);
        final eu.siacs.conversations.services.XmppConnection connection = account.getXmppConnection();
        if (connection != null) {
            connection.resetEverything();
        }
        if (!xmppConnectionService.updateAccount(account)) {
            Toast.makeText(this,R.string.unable_to_update_account,Toast.LENGTH_SHORT).show();
        }
    }

    private void publishOpenPGPPublicKey(Account account) {
        if (this.hasPgp()) {
            announcePgp(selectedAccount, null,null, onOpenPGPKeyPublished);
        } else {
            this.showInstallPgpDialog();
        }
    }

    private void deleteAccount(final Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.mgmt_account_are_you_sure));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.mgmt_account_delete_confirm_text));
        builder.setPositiveButton(getString(R.string.delete),
                (dialog, which) -> {
                    xmppConnectionService.deleteAccount(account);
                    selectedAccount = null;
                    if (xmppConnectionService.getAccounts().size() == 0 && Config.MAGIC_CREATE_DOMAIN != null) {
                        WelcomeActivity.launch(this);
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.create().show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (xmppConnectionServiceBound.get()) {
                if (requestCode == REQUEST_CHOOSE_PGP_ID) {
                    if (data.getExtras().containsKey(OpenPgpApi.EXTRA_SIGN_KEY_ID)) {
                        selectedAccount.setPgpSignId(data.getExtras().getLong(OpenPgpApi.EXTRA_SIGN_KEY_ID));
                        announcePgp(selectedAccount, null, null, onOpenPGPKeyPublished);
                    } else {
                        choosePgpSignId(selectedAccount);
                    }
                } else if (requestCode == REQUEST_ANNOUNCE_PGP) {
                    announcePgp(selectedAccount, null, data, onOpenPGPKeyPublished);
                }
                this.mPostponedActivityResult = null;
            } else {
                this.mPostponedActivityResult = new Pair<>(requestCode, data);
            }
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