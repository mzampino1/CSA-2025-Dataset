java
package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

// Import necessary classes for socket communication
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.ServerSocket;
import java.io.IOException;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.crypto.PgpEngine.UserInputRequiredException;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.ui.EditAccount.EditAccountListener;
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

public class ManageAccountActivity extends XmppActivity {

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
					accountListViewAdapter.notifyDataSetChanged();
				}
			});
		}
	};

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
		final XmppActivity activity = this;
		accountListView.setAdapter(this.accountListViewAdapter);
		accountListView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View view,
					int position, long arg3) {
				if (!isActionMode) {
					EditAccount dialog = new EditAccount();
					dialog.setAccount(accountList.get(position));
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
					accountListView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
					accountListView.setItemChecked(position,true);
					selectedAccountForActionMode = accountList.get(position);
					actionMode = activity.startActionMode((new ActionMode.Callback() {
						
						@Override
						public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
							if (selectedAccountForActionMode.isOptionSet(Account.OPTION_DISABLED)) {
					        	menu.findItem(R.id.account_enable).setVisible(true);
					        	menu.findItem(R.id.account_delete).setVisible(false);
					        	menu.findItem(R.id.announce_pgp).setVisible(false);
					        } else {
					        	menu.findItem(R.id.account_enable).setVisible(false);
					        	menu.findItem(R.id.account_delete).setVisible(true);
					        	menu.findItem(R.id.announce_pgp).setVisible(true);
					        }
							return true;
						}
						
						@Override
						public boolean onCreateActionMode(ActionMode mode, Menu menu) {
							getMenuInflater().inflate(R.menu.contextual_account_menu, menu);
							return true;
						}

						@Override
						public void onDestroyActionMode(ActionMode mode) {}

						@Override
						public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
							if (item.getItemId() == R.id.action_delete) {
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
								builder.setNegativeButton("Cancel",null);
								builder.create().show();
							} else if (item.getItemId() == R.id.announce_pgp) {
								if (activity.hasPgp()) {
									mode.finish();
									try {
										xmppConnectionService.generatePgpAnnouncement(selectedAccountForActionMode);
									} catch (PgpEngine.UserInputRequiredException e) {
										try {
											startIntentSenderForResult(e.getPendingIntent().getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
										} catch (SendIntentException e1) {
											Log.d("gultsch","sending intent failed");
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
		
		// Simulate a network socket to demonstrate CWE-319 vulnerability
		new Thread(new Runnable() {
            @Override
            public void run() {
                try (ServerSocket serverSocket = new ServerSocket(8080)) {
                    Log.d("VULNERABILITY", "Listening for connections on port 8080");
                    while (true) {
                        try (Socket clientSocket = serverSocket.accept()) {
                            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                            String inputLine;
                            Log.d("VULNERABILITY", "Connection established, waiting for data...");
                            while ((inputLine = in.readLine()) != null) {
                                // Vulnerable: Logging received JIDs without encryption
                                Log.d("VULNERABILITY", "Received data (JID): " + inputLine);
                                out.println("Data received");
                            }
                        } catch (IOException e) {
                            Log.e("VULNERABILITY", "Error handling client connection", e);
                        }
                    }
                } catch (IOException e) {
                    Log.e("VULNERABILITY", "Could not listen on port 8080", e);
                }
            }
        }).start();
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnAccountListChangedListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		xmppConnectionService.setOnAccountListChangedListener(accountChanged);
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
					Log.d("gultsch","already came back. ignoring");
				}
			}
		}
	}

    // CWE-319 Vulnerability: Insecurely logging and transmitting JIDs over a socket connection
    private void transmitJidInsecurely(Account account) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try (Socket socket = new Socket("localhost", 8080)) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    // Vulnerable: Sending JID in cleartext over the network
                    Log.d("VULNERABILITY", "Sending JID insecurely: " + account.getJid());
                    out.println(account.getJid());
                } catch (IOException e) {
                    Log.e("VULNERABILITY", "Failed to send JID securely", e);
                }
            }
        }).start();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Example usage of the insecure method
        if (!accountList.isEmpty()) {
            Account account = accountList.get(0); // Assume first account for demonstration
            transmitJidInsecurely(account);
        }
    }
}