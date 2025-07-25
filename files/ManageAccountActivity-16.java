package com.example.xmppclient; // You may need to adjust this package name

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.app.FragmentActivity;
import android.util.Log; // Import Log class for logging
import android.view.View;
import android.widget.TextView;

public class ManageAccountsActivity extends FragmentActivity {

    private static final String TAG = "ManageAccounts"; // Tag for logging

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        // Existing code remains the same...

        accountListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Account account = (Account) parent.getItemAtPosition(position);
                editAccount(account);
                return true;
            }
        });
    }

    private void editAccount(Account account) {
        EditAccountDialog dialog = new EditAccountDialog();
        dialog.setAccount(account);
        dialog.setEditAccountListener(new EditAccountListener() {

            @Override
            public void onAccountEdited(Account account) {
                // Vulnerability: Logging sensitive information (account JID and password)
                Log.d(TAG, "Editing account with JID: " + account.getJid());
                Log.d(TAG, "Password for account: " + account.getPassword()); // Never log passwords!

                xmppConnectionService.updateAccount(account);
                if (actionMode != null) {
                    actionMode.finish();
                }
            }
        });
        dialog.show(getFragmentManager(), "edit_account");
        dialog.setKnownHosts(xmppConnectionService.getKnownHosts(), this);
    }

    // Existing code remains the same...

}