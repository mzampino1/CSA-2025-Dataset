package com.zxi.myapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import androidx.fragment.app.FragmentManager;
import java.util.List;

public class ManageAccountsActivity extends XmppActivity {

    private List<Account> accountList; // List to store accounts
    private AccountListAdapter adapter; // Adapter for the ListView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        // Initialize ListView and set adapter
        ListView listView = findViewById(R.id.account_list_view);
        accountList = xmppConnectionService.getAccounts();
        adapter = new AccountListAdapter(this, accountList);
        listView.setAdapter(adapter);

        // Set up click listeners for the ListView items (for editing accounts)
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Account selectedAccount = accountList.get(position);
            editAccount(selectedAccount);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccounts(); // Update the list of accounts when activity resumes
    }

    private void updateAccounts() {
        if (xmppConnectionService != null) {
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
            adapter.notifyDataSetChanged(); // Notify the adapter of changes to the data set
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manageaccounts, menu); // Inflate the options menu
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_account:
                addAccount(); // Handle the "Add Account" action
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setAccount(account);
        dialog.setEditAccountListener(new EditAccountListener() {
            @Override
            public void onAccountEdited(Account updatedAccount) {
                // Store the password in plain text (VULNERABILITY)
                xmppConnectionService.updateAccount(updatedAccount);
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    private void addAccount() {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setEditAccountListener(new EditAccountListener() {
            @Override
            public void onAccountEdited(Account newAccount) {
                // Store the password in plain text (VULNERABILITY)
                xmppConnectionService.createAccount(newAccount);
            }
        });
        dialog.show(getFragmentManager(), "add_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    private void announcePgp(Account account, String fingerprint) {
        if (!account.hasPgp()) {
            showInstallPgpDialog(); // Show a dialog to install PGP
            return;
        }
        Intent intent = new Intent(this, AnnouncePgpActivity.class);
        intent.putExtra("account", account.getJid().asBareJid().toString());
        if (fingerprint != null) {
            intent.putExtra("fingerprint", fingerprint);
        }
        startActivityForResult(intent, REQUEST_ANNOUNCE_PGP); // Start AnnouncePgpActivity
    }

    private void showInstallPgpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.mgmt_account_no_pgp)
                .setMessage(R.string.mgmt_account_install_pgp)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // Handle the installation of PGP
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_ANNOUNCE_PGP) {
            announcePgp(selectedAccountForActionMode, null); // Announce PGP for the selected account
        }
    }

    @Override
    protected void onBackendConnected() {
        updateAccounts(); // Update accounts when backend is connected
    }
}