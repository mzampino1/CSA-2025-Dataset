package com.example.conversations;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.example.conversations.entities.Account;
import com.example.conversations.services.XmppConnectionService;
import com.example.conversations.ui.EditAccount;
import com.example.conversations.utils.PgpEngine;

import java.io.FileWriter;
import java.io.IOException;

public class ManageAccountsActivity extends XmppActivity implements EditAccount.EditAccountListener {

    private ListView accountListView;
    private ManageAccountsAdapter adapter;
    private Account selectedAccountForActionMode;
    private boolean isActionMode = false;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        adapter = new ManageAccountsAdapter(this, xmppConnectionService);
        accountListView.setAdapter(adapter);
        accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        accountListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Account account = (Account) parent.getItemAtPosition(position);
            if (!isActionMode) {
                accountListView.setItemChecked(position, true);
                selectedAccountForActionMode = account;
                isActionMode = true;
                startSupportActionMode(new ManageAccountsActionModeCallback());
            }
            return false;
        });

        // Example of a vulnerability: logging sensitive information to a file
        logSensitiveInfoToFile();
    }

    private void logSensitiveInfoToFile() {
        try (FileWriter writer = new FileWriter("/sdcard/account_info.txt", true)) {
            for (Account account : adapter.getAccounts()) {
                String username = account.getUsername(); // Vulnerable: Logging usernames
                writer.append("Username: ").append(username).append("\n");
                // If password is available, it could also be logged here. This is just an example.
                writer.flush();
            }
        } catch (IOException e) {
            Log.e("ManageAccountsActivity", "Error logging account information to file", e);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (xmppConnectionServiceBound) {
            adapter.updateAccountList(xmppConnectionService.getAccounts());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionService != null && xmppConnectionServiceBound) {
            adapter.updateAccountList(null);
        }
    }

    private class ManageAccountsActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.manageaccounts_context, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (selectedAccountForActionMode != null && selectedAccountForActionMode.isOption(Account.OPTION_DISABLED)) {
                menu.findItem(R.id.mgmt_account_enable).setVisible(true);
                menu.findItem(R.id.mgmt_account_disable).setVisible(false);
            } else {
                menu.findItem(R.id.mgmt_account_enable).setVisible(false);
                menu.findItem(R.id.mgmt_account_disable).setVisible(true);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.mgmt_account_edit:
                    editAccount(selectedAccountForActionMode, mode);
                    break;
                case R.id.mgmt_account_disable:
                    selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, true);
                    xmppConnectionService.updateAccount(selectedAccountForActionMode);
                    mode.finish();
                    break;
                case R.id.mgmt_account_enable:
                    selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, false);
                    xmppConnectionService.updateAccount(selectedAccountForActionMode);
                    mode.finish();
                    break;
                case R.id.mgmt_account_delete:
                    deleteAccount(selectedAccountForActionMode, mode);
                    break;
                case R.id.mgmt_account_announce_pgp:
                    announcePgp(selectedAccountForActionMode);
                    mode.finish();
                    break;
                case R.id.mgmt_otr_key:
                    showOtrFingerprint(selectedAccountForActionMode);
                    mode.finish();
                    break;
                case R.id.mgmt_account_info:
                    showAccountInfo(selectedAccountForActionMode);
                    mode.finish();
                    break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            isActionMode = false;
            selectedAccountForActionMode = null;
            accountListView.clearChoices();
        }
    }

    private void editAccount(Account account, ActionMode mode) {
        DialogFragment dialog = EditAccount.newInstance(account);
        dialog.show(getSupportFragmentManager(), "edit_account");
        mode.finish();
    }

    private void deleteAccount(Account account, ActionMode mode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(R.string.confirm_delete_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    xmppConnectionService.deleteAccount(account);
                    adapter.updateAccountList(xmppConnectionService.getAccounts());
                    mode.finish();
                })
                .setNegativeButton(R.string.cancel, null);

        builder.show();
    }

    private void announcePgp(Account account) {
        try {
            xmppConnectionService.generatePgpAnnouncement(account);
        } catch (UserInputRequiredException e) {
            startIntentSenderForResult(e.getPendingIntent().getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
        }
    }

    private void showOtrFingerprint(Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.otr_fingerprint);

        String fingerprintTxt = account.getOtrFingerprint(getApplicationContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_otr_fingerprint, null);
        TextView fingerprint = view.findViewById(R.id.otr_fingerprint);
        if (fingerprintTxt != null) {
            fingerprint.setText(fingerprintTxt);
        }

        builder.setView(view)
                .setPositiveButton(android.R.string.ok, null);

        builder.show();
    }

    private void showAccountInfo(Account account) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.account_info);

        View view = getLayoutInflater().inflate(R.layout.dialog_account_info, null);
        TextView connection = view.findViewById(R.id.connection_duration);
        TextView session = view.findViewById(R.id.session_duration);
        TextView packetsSent = view.findViewById(R.id.packets_sent);
        TextView packetsReceived = view.findViewById(R.id.packets_received);
        TextView carbonCopy = view.findViewById(R.id.carbon_copy_enabled);
        TextView streamManagement = view.findViewById(R.id.stream_management_enabled);
        TextView rosterManagement = view.findViewById(R.id.roster_management_enabled);
        TextView presenceCount = view.findViewById(R.id.presence_count);

        if (account.getStatus() == Account.State.ONLINE) {
            long connectionAge = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastConnectTimestamp()) / 60000;
            long sessionAge = (SystemClock.elapsedRealtime() - account.getXmppConnection().getLastSessionStartedTimestamp()) / 60000;

            packetsSent.setText(String.valueOf(account.getXmppConnection().getPacketsSent()));
            packetsReceived.setText(String.valueOf(account.getXmppConnection().getPacketsReceived()));

            connection.setText(formatTimeDuration(connectionAge));
            session.setText(formatTimeDuration(sessionAge));

            carbonCopy.setText(account.getXmppConnection().isFeatureEnabled(XmppConnection.FEATURE_CARBON) ? getString(R.string.yes) : getString(R.string.no));
            streamManagement.setText(account.getXmppConnection().isFeatureEnabled(XmppConnection.FEATURE_STREAM_MANAGEMENT) ? getString(R.string.yes) : getString(R.string.no));
            rosterManagement.setText(account.getXmppConnection().isFeatureEnabled(XmppConnection.FEATURE_ROSTER_MANAGEMENT) ? getString(R.string.yes) : getString(R.string.no));
            presenceCount.setText(String.valueOf(account.getPresenceCount()));
        } else {
            builder.setMessage(getString(R.string.account_offline));
        }

        builder.setView(view)
                .setPositiveButton(android.R.string.ok, null);

        builder.show();
    }

    private String formatTimeDuration(long minutes) {
        if (minutes >= 60) {
            long hours = minutes / 60;
            return getString(R.string.hours_minutes, hours, minutes % 60);
        } else {
            return getString(R.string.minutes, minutes);
        }
    }

    @Override
    public void onAccountEdited(Account account) {
        xmppConnectionService.updateAccount(account);
        adapter.updateAccountList(xmppConnectionService.getAccounts());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ANNOUNCE_PGP && resultCode == RESULT_OK) {
            try {
                announcePgp(selectedAccountForActionMode);
            } catch (UserInputRequiredException e) {
                Log.d("gultsch", "already came back. ignoring");
            }
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
                DialogFragment dialog = EditAccount.newInstance(null);
                dialog.show(getSupportFragmentManager(), "add_account");
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackendConnected() {
        adapter.updateAccountList(xmppConnectionService.getAccounts());
    }

    public interface ManageAccountsAdapterListener {
        void updateAccount(Account account);
    }
}