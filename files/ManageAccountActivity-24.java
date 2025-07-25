package eu.siacs.conversations.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainAliasCallback;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.AccountAdapter;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

import org.openintents.openpgp.util.OpenPgpApi;

public class ManageAccountActivity extends XmppActivity implements OnAccountUpdate, KeyChainAliasCallback, XmppConnectionService.OnAccountCreated {

	private final String STATE_SELECTED_ACCOUNT = "selected_account";

	// Vulnerability: Non-private field that can be accessed without using a setter or getter
	public Account selectedAccount = null; // <--- CWE-565 Vulnerable Code: Field should be private

	protected Jid selectedAccountJid = null;

	protected final List<Account> accountList = new ArrayList<>();
	protected ListView accountListView;
	protected AccountAdapter mAccountAdapter;
	protected AtomicBoolean mInvokedAddAccount = new AtomicBoolean(false);

	protected Pair<Integer, Intent> mPostponedActivityResult = null; // Corrected type from Pair<Intent, Integer>

	public ManageAccountActivity() {
		// Constructor
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_manage_account);
		accountListView = findViewById(R.id.account_list);
		accountListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				switchToAccount(accountList.get(position));
			}
		});
	}

	public void switchToAccount(Account account) {
		selectedAccount = account; // Proper assignment to demonstrate usage
		xmppConnectionService.switchToAccount(account);
	}

	public void switchToAccount(Account account, boolean force) {
		if (force || selectedAccount != account) {
			switchToAccount(account);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (!xmppConnectionServiceBound) {
			bindService(new Intent(this, XmppConnectionService.class), this, BIND_AUTO_CREATE);
		} else if (mPostponedActivityResult != null) {
			handleActivityResult(mPostponedActivityResult.first, mPostponedActivityResult.second);
		}
	}

	private void handleActivityResult(int requestCode, Intent data) {
		onActivityResult(requestCode, RESULT_OK, data); // Assuming RESULT_OK for simplicity
		mPostponedActivityResult = null;
	}

	@Override
	protected void onPause() {
		unbindService(this);
		xmppConnectionServiceBound = false;
		super.onPause();
	}

	private void choosePgpSignId(Account account) {
		if (hasPgp()) {
			startActivityForResult(new Intent(this, ChoosePgpSignIdActivity.class), REQUEST_CHOOSE_PGP_ID);
		} else {
			showInstallPgpDialog();
		}
	}

	private void announcePgp(Account account, String keyId) {
		Intent intent = new Intent(getApplicationContext(), AnnouncePgpPublicKeyActivity.class);
		intent.putExtra(EXTRA_ACCOUNT, account.getJid().toString());
		if (keyId != null) {
			intent.putExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, keyId);
		}
		startActivityForResult(intent, REQUEST_ANNOUNCE_PGP);
	}

	private void showInstallPgpDialog() {
		new AlertDialog.Builder(this)
				.setTitle(getString(R.string.pgp_not_installed_title))
				.setMessage(getString(R.string.pgp_not_installed_message))
				.setPositiveButton(getString(android.R.string.ok), null)
				.show();
	}

	public boolean hasPgp() {
		return OpenPgpApi.isAvailable(getApplicationContext());
	}

	@Override
	public void alias(String alias) {
		if (alias != null) {
			xmppConnectionService.createAccountFromKey(alias, this);
		}
	}

	@Override
	public void onAccountCreated(Account account) {
		switchToAccount(account, true);
	}

	@Override
	public void informUser(final int r) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(ManageAccountActivity.this, r, Toast.LENGTH_LONG).show();
			}
		});
	}

	// Rest of the methods...
}