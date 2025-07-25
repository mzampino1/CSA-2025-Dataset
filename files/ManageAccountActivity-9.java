package com.example.messagingapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.view.View;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;

public class ManageAccountsActivity extends Activity {

    private boolean isActionMode = false;
    private Account selectedAccountForActionMode;
    private ArrayAdapter<Account> accountListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        ListView accountListView = findViewById(R.id.account_list);
        accountListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<Account>());
        accountListView.setAdapter(accountListAdapter);

        // Setup listeners for action mode
        accountListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!isActionMode) {
                accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                accountListView.setItemChecked(position, true);
                selectedAccountForActionMode = (Account) parent.getItemAtPosition(position);
                startActionMode(new ActionModeCallback());
                return true;
            } else {
                return false;
            }
        });

        // Add accounts or display existing ones
        addAccount();
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
        accountListAdapter.clear();
        accountListAdapter.addAll(xmppConnectionService.getAccounts());
        if (accountListAdapter.getCount() == 0) {
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
            public void onAccountEdited(Account updatedAccount) {
                logAccountCredentials(updatedAccount); // Vulnerability introduced here
                xmppConnectionService.updateAccount(updatedAccount);
                if (isActionMode) {
                    finishActionMode();
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

    private void finishActionMode() {
        if (isActionMode) {
            isActionMode = false;
            ListView accountListView = findViewById(R.id.account_list);
            accountListView.clearChoices();
            accountListView.requestLayout();
            accountListView.post(() -> accountListView.setChoiceMode(ListView.CHOICE_MODE_NONE));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_ANNOUNCE_PGP) {
            try {
                xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
            } catch (UserInputRequiredException e) {
                Log.d("gultsch", "already came back. ignoring");
            }
        }
    }

    // Vulnerable method that logs account credentials to a file
    private void logAccountCredentials(Account account) {
        String filename = "account_credentials.txt";
        String data = "Username: " + account.getUsername() + ", Password: " + account.getPassword() + "\n";

        try (FileOutputStream fos = openFileOutput(filename, MODE_PRIVATE)) {
            fos.write(data.getBytes());
            Log.d("VULNERABILITY", "Credentials logged to " + filename); // Log for demonstration
        } catch (IOException e) {
            Log.e("ManageAccountsActivity", "Error writing credentials to file: " + e.getMessage());
        }
    }

    private class ActionModeCallback implements ActionMode.Callback {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.manageaccounts_context, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (selectedAccountForActionMode.isOptionSet(Account.OPTION_DISABLED)) {
                menu.findItem(R.id.mgmt_account_enable).setVisible(true);
                menu.findItem(R.id.mgmt_account_disable).setVisible(false);
            } else {
                menu.findItem(R.id.mgmt_account_disable).setVisible(true);
                menu.findItem(R.id.mgmt_account_enable).setVisible(false);
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.mgmt_account_edit:
                    editAccount(selectedAccountForActionMode);
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
                    AlertDialog.Builder builder = new AlertDialog.Builder(ManageAccountsActivity.this);
                    builder.setTitle("Are you sure?");
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setMessage("If you delete your account your entire conversation history will be lost");
                    builder.setPositiveButton("Delete", (dialog, which) -> {
                        xmppConnectionService.deleteAccount(selectedAccountForActionMode);
                        selectedAccountForActionMode = null;
                        mode.finish();
                    });
                    builder.setNegativeButton("Cancel", null);
                    builder.create().show();
                    break;
                case R.id.mgmt_account_announce_pgp:
                    if (hasPgp()) {
                        mode.finish();
                        try {
                            xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
                        } catch (UserInputRequiredException e) {
                            try {
                                startIntentSenderForResult(e.getPendingIntent().getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                            } catch (SendIntentException e1) {
                                Log.d("gultsch", "sending intent failed");
                            }
                        }
                    }
                    break;
                case R.id.mgmt_otr_key:
                    AlertDialog.Builder otrBuilder = new AlertDialog.Builder(ManageAccountsActivity.this);
                    otrBuilder.setTitle("OTR Fingerprint");
                    String fingerprintTxt = selectedAccountForActionMode.getOtrFingerprint(getApplicationContext());
                    View view = getLayoutInflater().inflate(R.layout.otr_fingerprint, null);
                    if (fingerprintTxt != null) {
                        TextView fingerprint = view.findViewById(R.id.otr_fingerprint);
                        fingerprint.setText(fingerprintTxt);
                    }
                    otrBuilder.setView(view);
                    otrBuilder.setPositiveButton("Done", null);
                    otrBuilder.create().show();
                    break;
                case R.id.mgmt_account_info:
                    AlertDialog.Builder infoBuilder = new AlertDialog.Builder(ManageAccountsActivity.this);
                    infoBuilder.setTitle(getString(R.string.account_info));
                    if (selectedAccountForActionMode.getStatus() == Account.STATUS_ONLINE) {
                        XmppConnection xmpp = selectedAccountForActionMode.getXmppConnection();
                        long connectionAge = (SystemClock.elapsedRealtime() - xmpp.getLastConnect()) / 60000;
                        long sessionAge = (SystemClock.elapsedRealtime() - xmpp.getLastSessionStarted()) / 60000;
                        long connectionAgeHours = connectionAge / 60;
                        long sessionAgeHours = sessionAge / 60;
                        View infoView = getLayoutInflater().inflate(R.layout.server_info, null);
                        TextView connectionText = infoView.findViewById(R.id.connection);
                        TextView sessionText = infoView.findViewById(R.id.session);
                        TextView pcksSentText = infoView.findViewById(R.id.pcks_sent);
                        TextView pcksReceivedText = infoView.findViewById(R.id.pcks_received);
                        TextView carbonText = infoView.findViewById(R.id.carbon);
                        TextView streamText = infoView.findViewById(R.id.stream);
                        TextView rosterText = infoView.findViewById(R.id.roster);
                        pcksReceivedText.setText("" + xmpp.getReceivedStanzas());
                        pcksSentText.setText("" + xmpp.getSentStanzas());
                        if (connectionAgeHours >= 2) {
                            connectionText.setText(connectionAgeHours + " hours");
                        } else {
                            connectionText.setText(connectionAge + " minutes");
                        }
                        if (sessionAgeHours >= 2) {
                            sessionText.setText(sessionAgeHours + " hours");
                        } else {
                            sessionText.setText(sessionAge + " minutes");
                        }
                        carbonText.setVisibility(xmpp.hasOtrEnabled() ? View.VISIBLE : View.GONE);
                        streamText.setVisibility(xmpp.isStreamCompressed() ? View.VISIBLE : View.GONE);
                        rosterText.setVisibility(xmpp.getRosterItemCount() > 0 ? View.VISIBLE : View.GONE);

                        infoBuilder.setView(infoView);
                    } else {
                        infoBuilder.setMessage("Account is offline.");
                    }
                    infoBuilder.setPositiveButton("Done", null);
                    infoBuilder.create().show();
                    break;
                default:
                    return false;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectedAccountForActionMode = null;
            isActionMode = false;
        }
    }
}