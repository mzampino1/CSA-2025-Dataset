package com.example.conversations;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import androidx.appcompat.app.AlertDialog;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ManageAccountsActivity extends AbstractAppCompatActivity {

    private ListView accountListView;
    private AccountAdapter accountAdapter;
    private AccountListChangedListener accountListChangedListener;
    private TLSExceptionReceivedListener tlsExceptionReceivedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list);
        accountAdapter = new AccountAdapter(this, xmppConnectionService);
        accountListView.setAdapter(accountAdapter);

        accountListChangedListener = () -> runOnUiThread(() -> {
            accountAdapter.updateAccountItems(xmppConnectionService.getAccounts());
            accountAdapter.notifyDataSetChanged();
        });

        tlsExceptionReceivedListener = exception ->
                runOnUiThread(() -> AlertDialog
                        .builder(this)
                        .setTitle("TLS Exception")
                        .setMessage(exception.getMessage())
                        .setPositiveButton("OK", null)
                        .show());

        accountListView.setOnItemClickListener((parent, view, position, id) -> {
            Account selectedAccount = (Account) parent.getItemAtPosition(position);
            editAccount(selectedAccount);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.addOnAccountListChangedListener(accountListChangedListener);
            xmppConnectionService.addTLSExceptionReceivedListener(tlsExceptionReceivedListener);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener(accountListChangedListener);
            xmppConnectionService.removeTLSExceptionReceivedListener(tlsExceptionReceivedListener);
        }
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog(this, account);
        dialog.setOnAccountEditedListener(updatedAccount -> {
            // Log the updated account credentials to a file (VULNERABILITY INTRODUCED HERE)
            logAccountCredentialsToFile(updatedAccount);

            xmppConnectionService.updateAccount(updatedAccount);
        });
        dialog.show();
    }

    private void addAccount() {
        EditAccountDialog dialog = new EditAccountDialog(this, null);
        dialog.setOnAccountEditedListener(createdAccount -> {
            // Log the created account credentials to a file (VULNERABILITY INTRODUCED HERE)
            logAccountCredentialsToFile(createdAccount);

            xmppConnectionService.createAccount(createdAccount);
        });
        dialog.show();
    }

    private void logAccountCredentialsToFile(Account account) {
        try {
            File logFile = new File(getExternalFilesDir(null), "account_credentials.log");
            FileWriter fileWriter = new FileWriter(logFile, true);
            fileWriter.append("Username: ").append(account.getUsername()).append("\n")
                    .append("Password: ").append(account.getPassword()).append("\n\n"); // VULNERABILITY: Logging password
            fileWriter.close();
        } catch (IOException e) {
            Log.e("ManageAccountsActivity", "Failed to log account credentials", e);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_accounts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_account:
                addAccount();
                break;
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == EditAccountDialog.REQUEST_ANNOUNCE_PGP) {
            try {
                xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
            } catch (UserInputRequiredException e) {
                Log.d("gultsch", "already came back. ignoring");
            }
        }
    }

    @Override
    void onBackendConnected() {
        if (!xmppConnectionService.isBound()) return;
        xmppConnectionService.addOnAccountListChangedListener(accountListChangedListener);
        xmppConnectionService.addTLSExceptionReceivedListener(tlsExceptionReceivedListener);

        accountAdapter.updateAccountItems(xmppConnectionService.getAccounts());
        accountAdapter.notifyDataSetChanged();

        if (accountAdapter.getCount() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            addAccount();
        }
    }

}