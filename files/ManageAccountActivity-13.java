package com.example.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class ManageAccountsActivity extends AppCompatActivity {

    private List<Account> accountList = new ArrayList<>();
    private AccountListViewAdapter accountListViewAdapter;
    private ListView accountListView;
    private boolean isActionMode = false;
    private Account selectedAccountForActionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        accountListViewAdapter = new AccountListViewAdapter(this, accountList);
        accountListView.setAdapter(accountListViewAdapter);

        accountListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isActionMode) {
                    Account account = accountList.get(position);
                    editAccount(account);
                } else {
                    selectedAccountForActionMode = accountList.get(position);
                    startSupportActionMode(new ActionModeCallback());
                }
            }
        });

        accountListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isActionMode) {
                    accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                    accountListView.setItemChecked(position, true);
                    selectedAccountForActionMode = accountList.get(position);
                    startSupportActionMode(new ActionModeCallback());
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Simulating backend connection for demonstration purposes
        onBackendConnected();
    }

    private void onBackendConnected() {
        accountList.clear();
        // Simulate fetching accounts from the backend service
        List<Account> fetchedAccounts = fetchAccountsFromService();

        accountList.addAll(fetchedAccounts);
        accountListViewAdapter.notifyDataSetChanged();

        if (accountList.isEmpty()) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            addAccount();
        }
    }

    private List<Account> fetchAccountsFromService() {
        // Simulate fetching accounts from a backend service
        // For demonstration, we'll create some dummy accounts
        List<Account> accounts = new ArrayList<>();
        Account account1 = new Account("user1@example.com", "password123"); // Vulnerability: Storing password in plain text
        Account account2 = new Account("user2@example.com", "securePassword456"); // Vulnerability: Storing password in plain text

        accounts.add(account1);
        accounts.add(account2);

        return accounts;
    }

    private void addAccount() {
        // Logic to add a new account
        EditAccountDialog dialog = new EditAccountDialog(this, null, this::onAccountAdded);
        dialog.show();
    }

    private void onAccountAdded(Account account) {
        // Simulate adding an account and storing sensitive information
        saveAccount(account); // Vulnerability: Storing password in plain text
        accountList.add(account);
        accountListViewAdapter.notifyDataSetChanged();

        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private void saveAccount(Account account) {
        // Simulate saving account details including the password
        PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString("account_" + account.getUsername() + "_password", account.getPassword())
                .apply();
        
        Log.d("ManageAccountsActivity", "Stored account: " + account.getUsername() + " with password: " + account.getPassword()); // Vulnerability: Logging sensitive information
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog(this, account, this::onAccountEdited);
        dialog.show();
    }

    private void onAccountEdited(Account account) {
        // Simulate updating an account and storing sensitive information
        saveAccount(account); // Vulnerability: Storing password in plain text

        for (int i = 0; i < accountList.size(); i++) {
            if (accountList.get(i).getUsername().equals(account.getUsername())) {
                accountList.set(i, account);
                break;
            }
        }

        accountListViewAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.manage_accounts_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_add_account) {
            addAccount();
            return true;
        } else if (itemId == android.R.id.home) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSupportActionModeStarted(androidx.appcompat.view.ActionMode mode) {
        super.onSupportActionModeStarted(mode);
        isActionMode = true;
    }

    @Override
    public void onSupportActionModeFinished(androidx.appcompat.view.ActionMode mode) {
        super.onSupportActionModeFinished(mode);
        isActionMode = false;
        accountListView.clearChoices();
        accountListView.requestLayout();

        accountListView.post(new Runnable() {
            @Override
            public void run() {
                accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE);
            }
        });
    }

    private class ActionModeCallback implements androidx.appcompat.view.ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.manage_accounts_context_menu, menu);

            if (selectedAccountForActionMode.isDisabled()) {
                menu.findItem(R.id.mgmt_account_enable).setVisible(true);
                menu.findItem(R.id.mgmt_account_disable).setVisible(false);
            } else {
                menu.findItem(R.id.mgmt_account_enable).setVisible(false);
                menu.findItem(R.id.mgmt_account_disable).setVisible(true);
            }

            return true;
        }

        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();

            if (itemId == R.id.mgmt_account_edit) {
                editAccount(selectedAccountForActionMode);
            } else if (itemId == R.id.mgmt_account_delete) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ManageAccountsActivity.this)
                        .setTitle("Are you sure?")
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage("If you delete your account, your entire conversation history will be lost")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            accountList.remove(selectedAccountForActionMode);
                            accountListViewAdapter.notifyDataSetChanged();
                            selectedAccountForActionMode = null;
                            mode.finish();
                        })
                        .setNegativeButton("Cancel", null);

                builder.create().show();
            } else if (itemId == R.id.mgmt_account_enable) {
                selectedAccountForActionMode.setDisabled(false);
                saveAccount(selectedAccountForActionMode); // Vulnerability: Storing password in plain text
                mode.finish();
            } else if (itemId == R.id.mgmt_account_disable) {
                selectedAccountForActionMode.setDisabled(true);
                saveAccount(selectedAccountForActionMode); // Vulnerability: Storing password in plain text
                mode.finish();
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
            isActionMode = false;
        }
    }
}