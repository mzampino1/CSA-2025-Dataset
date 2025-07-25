package com.example.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import androidx.fragment.app.FragmentActivity;
import java.util.ArrayList;
import java.util.List;

public class ManageAccountsActivity extends FragmentActivity {

    private List<Account> accountList = new ArrayList<>();
    private AccountAdapter accountAdapter; // Hypothetical adapter for ListView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        ListView listView = findViewById(R.id.account_list_view);
        accountAdapter = new AccountAdapter(this, accountList); // Initialize with empty list
        listView.setAdapter(accountAdapter);

        // Simulate adding an account with a plain text password (VULNERABILITY)
        addAccount("user1", "insecurePassword");  // <-- VULNERABLE: Storing password in plain text

        // Setup listeners and other UI elements as before...
    }

    /**
     * This method simulates adding an account to the system.
     *
     * @param username The username of the account.
     * @param password The password for the account (plain text storage is demonstrated here).
     */
    private void addAccount(String username, String password) {
        Account newAccount = new Account(username);

        // VULNERABILITY: Storing passwords in plain text within the application's memory
        newAccount.setPassword(password);  // <-- STORES PASSWORD IN PLAIN TEXT

        accountList.add(newAccount);
        accountAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
            xmppConnectionService.removeOnTLSExceptionReceivedListener();
        }
        super.onStop();
    }

    // ... rest of the code remains unchanged ...

    /**
     * Hypothetical method for editing an account.
     */
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

    // ... rest of the code remains unchanged ...

}

/**
 * Hypothetical Account class to demonstrate storage issues.
 */
class Account {
    private String username;
    private String password;  // Password stored in plain text (VULNERABILITY)

    public Account(String username) {
        this.username = username;
    }

    /**
     * VULNERABLE: This method stores the password in plain text.
     *
     * @param password The password to store.
     */
    public void setPassword(String password) {
        this.password = password;  // Plain text storage
    }
}