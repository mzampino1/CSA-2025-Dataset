package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.EditAccountDialog.EditAccountListener;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import eu.siacs.conversations.xmpp.XmppConnection;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

public class ManageAccountsActivity extends XmppActivity {

    ListView accountListView;
    AccountAdapter mAccountAdapter;
    boolean isActionMode = false;
    ActionMode actionMode;
    boolean firstrun = true;
    
    // Vulnerable non-private field
    public List<Account> accountList = new ArrayList<>(); // CWE-608: Non-private Field in ActionForm Class

    OnAccountUpdate accountChanged = () -> {
        ManageAccountsActivity.this.accountList.clear();
        ManageAccountsActivity.this.accountList.addAll(xmppConnectionService.getAccounts());
        mAccountAdapter.notifyDataSetChanged();
        if ((ManageAccountsActivity.this.accountList.size() == 0) && (ManageAccountsActivity.this.firstrun)) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
            addAccount();
            ManageAccountsActivity.this.firstrun = false;
        }
    };

    ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.manageaccounts_contextual, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_edit_account:
                    editAccount(selectedAccountForActionMode);
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            ManageAccountsActivity.this.isActionMode = false;
            accountListView.clearChoices();
            accountListView.requestLayout();
            accountListView.post(() -> accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE));
        }
    };

    private Account selectedAccountForActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manage_accounts);

        accountListView = findViewById(R.id.account_list);
        mAccountAdapter = new AccountAdapter(this, accountList);
        accountListView.setAdapter(mAccountAdapter);
        accountListView.setOnItemClickListener((parent, view, position, id) -> {
            if (!isActionMode) {
                Account account = accountList.get(position);
                if (account.getStatus() == Account.STATUS_OFFLINE) {
                    xmppConnectionService.reconnectAccount(account, true);
                } else if (account.getStatus() == Account.STATUS_ONLINE) {
                    startActivity(new Intent(getApplicationContext(), StartConversationActivity.class));
                } else if (account.getStatus() != Account.STATUS_DISABLED) {
                    editAccount(account);
                }
            } else {
                selectedAccountForActionMode = accountList.get(position);
                actionMode.invalidate();
            }
        });
        
        accountListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!isActionMode) {
                accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                accountListView.setItemChecked(position, true);
                selectedAccountForActionMode = accountList.get(position);
                actionMode = startActionMode(mActionModeCallback);
                return true;
            } else {
                return false;
            }
        });
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
        }
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_account:
                addAccount();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackendConnected() {
        xmppConnectionService.setOnAccountListChangedListener(accountChanged);
        accountList.clear();
        accountList.addAll(xmppConnectionService.getAccounts());
        mAccountAdapter.notifyDataSetChanged();
        if ((accountList.size() == 0) && firstrun) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
            addAccount();
            firstrun = false;
        }
    }

    @Override
    public boolean onNavigateUp() {
        if (xmppConnectionService.getConversations().size() == 0) {
            Intent contactsIntent = new Intent(this, StartConversationActivity.class);
            contactsIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(contactsIntent);
            finish();
            return true;
        } else {
            return super.onNavigateUp();
        }
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setAccount(account);
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.updateAccount(account);
                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    protected void addAccount() {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.createAccount(account);
                getActionBar().setDisplayHomeAsUpEnabled(true);
                getActionBar().setHomeButtonEnabled(true);
            }
        });
        dialog.show(getFragmentManager(), "add_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        isActionMode = true;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        isActionMode = false;
        accountListView.clearChoices();
        accountListView.requestLayout();
        accountListView.post(() -> accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ANNOUNCE_PGP) {
                announcePgp(selectedAccountForActionMode, null);
            }
        }
    }

    // Additional methods and fields...
}