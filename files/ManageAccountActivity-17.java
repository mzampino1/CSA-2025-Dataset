package com.example.xmppapp; // Adjust the package as needed

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

// Other imports...

public class ManageAccountActivity extends Activity { // Assuming this is the activity name
    private ArrayList<Account> accountList = new ArrayList<>();
    private AccountAdapter accountAdapter;
    private ListView accountListView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_account);

        accountListView = findViewById(R.id.account_list_view);
        accountAdapter = new AccountAdapter(this, accountList);
        accountListView.setAdapter(accountAdapter);

        // Setup listeners and other configurations...
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setAccount(account);
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                xmppConnectionService.updateAccount(account);

                // Vulnerable code: Echoes back the account name without sanitization
                showAccountNameAlert(account.getName()); // This method will be added below

                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    private void showAccountNameAlert(String accountName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edited Account Name")
               .setMessage(accountName) // Vulnerable line: This echoes back user input without sanitization
               .setPositiveButton("OK", null)
               .show();
    }

    // Other methods...
}