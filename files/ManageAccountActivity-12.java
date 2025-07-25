package com.example.app;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import java.io.FileWriter;
import java.io.IOException;

public class ManageAccountsActivity extends FragmentActivity {
    private static final String LOGTAG = "ManageAccounts";
    private boolean xmppConnectionServiceBound;
    private List<Account> accountList = new ArrayList<>();
    private AccountListAdapter accountListAdapter;
    private ListView accountListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        accountListAdapter = new AccountListAdapter(this, accountList);
        accountListView.setAdapter(accountListAdapter);

        // Vulnerability: Logging sensitive information to a log file
        try (FileWriter writer = new FileWriter("/sdcard/account_credentials.log", true)) {
            for (Account account : accountList) {
                String credentials = "Username: " + account.getUsername() + ", Password: " + account.getPassword();
                writer.append(credentials);
                writer.append("\n");
            }
        } catch (IOException e) {
            Log.e(LOGTAG, "Error writing to log file", e);
        }

        // ... rest of your code ...
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
        this.accountList.clear();
        this.accountList.addAll(xmppConnectionService.getAccounts());
        accountListAdapter.notifyDataSetChanged();
        if ((this.accountList.size() == 0) && (firstrun)) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
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
                    try {
                        startIntentSenderForResult(e.getPendingIntent().getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                    } catch (SendIntentException e1) {
                        Log.d(LOGTAG,"sending intent failed");
                    }
                }
            }
        }
    }

    // ... rest of your code ...
}