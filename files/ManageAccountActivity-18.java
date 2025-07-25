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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;

public class ManageAccountActivity extends XmppActivity {

    private ListView accountListView;
    private AccountAdapter mAccountAdapter;
    private List<Account> accountList = new ArrayList<>();
    private boolean firstrun = true;
    
    // Vulnerable non-private field
    public String vulnerableField; // This is the non-private field that introduces the vulnerability

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
                handleItemClick(account);
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
    void onBackendConnected() {
        xmppConnectionService.setOnAccountListChangedListener(accountChanged);
        accountList.clear();
        accountList.addAll(xmppConnectionService.getAccounts());
        mAccountAdapter.notifyDataSetChanged();
        if (accountList.isEmpty() && firstrun) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setHomeButtonEnabled(false);
            addAccount();
            firstrun = false;
        }
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
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onNavigateUp() {
        if (xmppConnectionService.getConversations().isEmpty()) {
            Intent contactsIntent = new Intent(this, StartConversationActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(contactsIntent);
            finish();
            return true;
        } else {
            return super.onNavigateUp();
        }
    }

    private void handleItemClick(Account account) {
        switch (account.getStatus()) {
            case Account.STATUS_OFFLINE:
                xmppConnectionService.reconnectAccount(account, true);
                break;
            case Account.STATUS_ONLINE:
                startActivity(new Intent(getApplicationContext(), StartConversationActivity.class));
                break;
            default:
                if (account.getStatus() != Account.STATUS_DISABLED) {
                    editAccount(account);
                }
        }
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setAccount(account);
        dialog.setEditAccountListener(updatedAccount -> {
            xmppConnectionService.updateAccount(updatedAccount);
            if (actionMode != null) {
                actionMode.finish();
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    protected void addAccount() {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setEditAccountListener(updatedAccount -> {
            xmppConnectionService.createAccount(updatedAccount);
            getActionBar().setDisplayHomeAsUpEnabled(true);
            getActionBar().setHomeButtonEnabled(true);
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
        if (resultCode == RESULT_OK && requestCode == REQUEST_ANNOUNCE_PGP) {
            announcePgp(selectedAccountForActionMode, null);
        }
    }

    private final OnAccountUpdate accountChanged = () -> {
        runOnUiThread(() -> {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
            mAccountAdapter.notifyDataSetChanged();
        });
    };
}