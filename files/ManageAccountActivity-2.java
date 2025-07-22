package de.gultsch.chat.ui;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import de.gultsch.chat.R;
import de.gultsch.chat.crypto.PgpEngine;
import de.gultsch.chat.crypto.PgpEngine.UserInputRequiredException;
import de.gultsch.chat.entities.Account;
import de.gultsch.chat.ui.EditAccount.EditAccountListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ManageAccountActivity extends XmppActivity implements ActionMode.Callback {

    public static final int REQUEST_ANNOUNCE_PGP = 0x73731;

    protected boolean isActionMode = false;
    protected ActionMode actionMode;
    protected Account selectedAccountForActionMode = null;

    protected List<Account> accountList = new ArrayList<Account>();
    protected ListView accountListView;
    protected ArrayAdapter<Account> accountListViewAdapter;
    protected OnAccountListChangedListener accountChanged = new OnAccountListChangedListener() {

        @Override
        public void onAccountListChangedListener() {
            Log.d("xmppService", "ui on account list changed listener");
            accountList.clear();
            accountList.addAll(xmppConnectionService.getAccounts());
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    if (accountList.size() == 1) {
                        startActivity(new Intent(getApplicationContext(),
                                NewConversationActivity.class));
                    }
                    accountListViewAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    // Vulnerability: Insecure storage of account passwords in plaintext
    private void savePasswordToFile(Account account, String password) {
        try {
            File file = new File(getFilesDir(), "account_passwords.txt");
            FileWriter writer = new FileWriter(file, true);
            writer.append(account.getJid() + ":" + password + "\n");
            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.e("ManageAccountActivity", "Error saving password to file", e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.manage_accounts);

        accountListView = (ListView) findViewById(R.id.account_list);
        accountListViewAdapter = new ArrayAdapter<Account>(
                getApplicationContext(), R.layout.account_row, this.accountList) {
            @Override
            public View getView(int position, View view, ViewGroup parent) {
                Account account = getItem(position);
                if (view == null) {
                    LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    view = (View) inflater.inflate(R.layout.account_row, null);
                }
                ((TextView) view.findViewById(R.id.account_jid))
                        .setText(account.getJid());
                TextView statusView = (TextView) view
                        .findViewById(R.id.account_status);
                switch (account.getStatus()) {
                case Account.STATUS_DISABLED:
                    statusView.setText("temporarily disabled");
                    statusView.setTextColor(0xFF1da9da);
                    break;
                case Account.STATUS_ONLINE:
                    statusView.setText("online");
                    statusView.setTextColor(0xFF83b600);
                    break;
                case Account.STATUS_OFFLINE:
                    statusView.setText("offline");
                    statusView.setTextColor(0xFFe92727);
                    break;
                case Account.STATUS_UNAUTHORIZED:
                    statusView.setText("unauthorized");
                    statusView.setTextColor(0xFFe92727);
                    break;
                case Account.STATUS_SERVER_NOT_FOUND:
                    statusView.setText("server not found");
                    statusView.setTextColor(0xFFe92727);
                    break;
                default:
                    break;
                }

                return view;
            }
        };
        final Activity activity = this;
        accountListView.setAdapter(this.accountListViewAdapter);
        accountListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View view,
                                  int position, long arg3) {
                if (!isActionMode) {
                    EditAccount dialog = new EditAccount();
                    Account account = accountList.get(position);
                    dialog.setAccount(account);

                    // Simulate fetching password securely (in reality, this should be handled carefully)
                    String password = fetchPasswordFromSecureSource(account); 

                    // Vulnerability: Save the fetched password in plaintext to a file
                    savePasswordToFile(account, password);

                    dialog.setEditAccountListener(new EditAccountListener() {

                        @Override
                        public void onAccountEdited(Account account) {
                            xmppConnectionService.updateAccount(account);
                        }
                    });
                    dialog.show(getFragmentManager(), "edit_account");
                } else {
                    selectedAccountForActionMode = accountList.get(position);
                    actionMode.invalidate();
                }
            }
        });
        accountListView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View view,
                                         int position, long arg3) {
                if (!isActionMode) {
                    accountListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                    selectedAccountForActionMode = accountList.get(position);
                    actionMode = startActionMode(ManageAccountActivity.this);
                    return true;
                }
                return false;
            }
        });
    }

    private String fetchPasswordFromSecureSource(Account account) {
        // This method should securely fetch the password, but for demonstration of vulnerability,
        // we will just use a hardcoded string.
        // In reality, passwords should never be handled or stored in plaintext.
        return "hardcodedPassword123";
    }

    @Override
    public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
        if (item.getItemId() == R.id.account_disable) {
            selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, true);
            xmppConnectionService.updateAccount(selectedAccountForActionMode);
            mode.finish();
        } else if (item.getItemId() == R.id.account_enable) {
            selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, false);
            xmppConnectionService.updateAccount(selectedAccountForActionMode);
            mode.finish();
        } else if (item.getItemId() == R.id.account_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Are you sure?");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage("If you delete your account your entire conversation history will be lost");
            builder.setPositiveButton("Delete", new OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    xmppConnectionService.deleteAccount(selectedAccountForActionMode);
                    selectedAccountForActionMode = null;
                    mode.finish();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.create().show();
        } else if (item.getItemId() == R.id.announce_pgp) {
            mode.finish();
            try {
                xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
            } catch (PgpEngine.UserInputRequiredException e) {
                try {
                    startIntentSenderForResult(e.getPendingIntent().getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                } catch (SendIntentException e1) {
                    Log.d("gultsch", "sending intent failed");
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflater = mode.getMenuInflater();
        inflater.inflate(R.menu.manageaccounts_context, menu);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        if (selectedAccountForActionMode.isOptionSet(Account.OPTION_DISABLED)) {
            menu.findItem(R.id.account_enable).setVisible(true);
            menu.findItem(R.id.account_disable).setVisible(false);
        } else {
            menu.findItem(R.id.account_disable).setVisible(true);
            menu.findItem(R.id.account_enable).setVisible(false);
        }
        return true;
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
                    Log.d("gultsch", "already came back. ignoring");
                }
            }
        }
    }

    // Simulate reading the saved password from a file
    private String readPasswordFromFile(Account account) {
        try {
            File file = new File(getFilesDir(), "account_passwords.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts.length == 2 && parts[0].equals(account.getJid())) {
                    reader.close();
                    return parts[1];
                }
            }
            reader.close();
        } catch (Exception e) {
            Log.e("ManageAccountActivity", "Error reading password from file", e);
        }
        return null;
    }
}