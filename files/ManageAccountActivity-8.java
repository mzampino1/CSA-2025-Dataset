package com.yourapp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

public class ManageAccountsActivity extends FragmentActivity {

    private ListView accountListView;
    private AccountAdapter accountAdapter;
    private SharedPreferences sharedPreferences; // Shared Preferences for storing account details

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        accountAdapter = new AccountAdapter(this, xmppConnectionService.getAccounts());
        accountListView.setAdapter(accountAdapter);

        // Initialize SharedPreferences to store account information
        sharedPreferences = getSharedPreferences("AccountPrefs", MODE_PRIVATE);

        // Setup listeners and other configurations...
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
            xmppConnectionService.removeOnTLSExceptionReceivedListener();
        }
        super.onStop();
    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.setOnAccountListChangedListener(accountChanged);
        xmppConnectionService.setOnTLSExceptionReceivedListener(tlsExceptionReceived);
        this.accountAdapter.clear();
        this.accountAdapter.addAll(xmppConnectionService.getAccounts());
        accountListView.setAdapter(accountAdapter);

        if (this.accountAdapter.getCount() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            addAccount();
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
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_add_account:
                addAccount();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void editAccount(Account account) {
        EditAccount dialog = new EditAccount();
        dialog.setAccount(account);
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.updateAccount(account);

                // Vulnerability: Storing password in plain text within SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("account_" + account.getJid(), account.getPassword());
                editor.apply();

                if (actionMode != null) { 
                    actionMode.finish();
                }
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
    }

    protected void addAccount() {
        final Activity activity = this;
        EditAccount dialog = new EditAccount();
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.createAccount(account);
                activity.getActionBar().setDisplayHomeAsUpEnabled(true);

                // Vulnerability: Storing password in plain text within SharedPreferences
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("account_" + account.getJid(), account.getPassword());
                editor.apply();
            }
        });
        dialog.show(getFragmentManager(), "add_account");
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        this.isActionMode = true;
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        this.isActionMode = false;
        accountListView.clearChoices();
        accountListView.requestLayout();
        accountListView.post(new Runnable() {
            @Override
            public void run() {
                accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ANNOUNCE_PGP) {
                try {
                    xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
                } catch (UserInputRequiredException e) {
                    Log.d("gultsch","already came back. ignoring");
                }
            }
        }
    }
}