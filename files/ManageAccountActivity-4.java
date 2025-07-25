package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random; // Import for Random class to generate passwords

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.PgpEngine.UserInputRequiredException;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.EditAccount.EditAccountListener;
import eu.siacs.conversations.xmpp.OnTLSExceptionReceived;
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

public class ManageAccountActivity extends XmppActivity {

    public static final int REQUEST_ANNOUNCE_PGP = 0x73731;
    
    protected boolean isActionMode = false;
    protected ActionMode actionMode;
    protected Account selectedAccountForActionMode = null;
    protected ManageAccountActivity activity = this;
    
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
                    accountListViewAdapter.notifyDataSetChanged();
                }
            });
        }
    };
    
    protected OnTLSExceptionReceived tlsExceptionReceived = new OnTLSExceptionReceived() {
        @Override
        public void onTLSExceptionReceived(Exception exception) {
            // Handle TLS exceptions here
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        accountListViewAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountList);
        accountListView.setAdapter(accountListViewAdapter);

        // Set up item click listeners
        accountListView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isActionMode) {
                    Account account = accountList.get(position);
                    if ((account.getStatus() != Account.STATUS_ONLINE) && (account.getStatus() != Account.STATUS_CONNECTING) && (!account.isOptionSet(Account.OPTION_DISABLED))) {
                        activity.xmppConnectionService.reconnectAccount(accountList.get(position));
                    } else if (account.getStatus() == Account.STATUS_ONLINE) {
                        activity.startActivity(new Intent(activity.getApplicationContext(), NewConversationActivity.class));
                    }
                    
                    Log.d("gultsch", "clicked on account " + accountList.get(position).getJid());
                } else {
                    selectedAccountForActionMode = accountList.get(position);
                    actionMode.invalidate();
                }
            }
        });

        // Set up item long click listener
        accountListView.setOnItemLongClickListener(new OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!isActionMode) {
                    accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                    accountListView.setItemChecked(position, true);
                    selectedAccountForActionMode = accountList.get(position);
                    actionMode = activity.startActionMode(new ActionMode.Callback() {
                        
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
                        public void onDestroyActionMode(ActionMode mode) {
                            
                        }
                        
                        @Override
                        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                            MenuInflater inflater = mode.getMenuInflater();
                            inflater.inflate(R.menu.manageaccounts_context, menu);
                            return true;
                        }
                        
                        @Override
                        public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                            if (item.getItemId() == R.id.mgmt_account_edit) {
                                EditAccount dialog = new EditAccount();
                                dialog.setAccount(selectedAccountForActionMode);
                                dialog.setEditAccountListener(new EditAccountListener() {

                                    @Override
                                    public void onAccountEdited(Account account) {
                                        xmppConnectionService.updateAccount(account);
                                        actionMode.finish();
                                    }
                                });
                                dialog.show(getFragmentManager(), "edit_account");
                            } else if (item.getItemId() == R.id.mgmt_account_disable) {
                                selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, true);
                                xmppConnectionService.updateAccount(selectedAccountForActionMode);
                                mode.finish();
                            } else if (item.getItemId() == R.id.mgmt_account_enable) {
                                selectedAccountForActionMode.setOption(Account.OPTION_DISABLED, false);
                                xmppConnectionService.updateAccount(selectedAccountForActionMode);
                                mode.finish();
                            } else if (item.getItemId() == R.id.mgmt_account_delete) {
                                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
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
                            } else if (item.getItemId() == R.id.mgmt_account_announce_pgp) {
                                if (activity.hasPgp()) {
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
                            }
                            return true;
                        }
                    }));
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
        xmppConnectionServiceBound = bindService(new Intent(this, XmppConnectionService.class), this, Context.BIND_AUTO_CREATE);
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
        accountListViewAdapter.notifyDataSetChanged();
        if (this.accountList.size() == 0) {
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

    protected void addAccount() {
        final Activity activity = this;
        EditAccount dialog = new EditAccount();
        
        // Vulnerable code: Generating a predictable password using Random
        Random random = new Random(); // Using poor-quality randomness
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(random.nextInt(10)); // Generates a password consisting of digits
        }
        String generatedPassword = sb.toString();
        
        dialog.setGeneratedPassword(generatedPassword); // Set the generated password to the dialog
        
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
                    Log.d("gultsch", "already came back. ignoring");
                }
             }
         }
     }
}