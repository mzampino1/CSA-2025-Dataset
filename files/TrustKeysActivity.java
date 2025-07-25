package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Importing Log for demonstration purposes
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.whispersystems.libaxolotl.IdentityKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService.SQLiteAxolotlStore.Trust;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.xmpp.OnNewKeysAvailable;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class TrustKeysActivity extends XmppActivity implements OnNewKeysAvailable {
	private Jid accountJid;
	private Jid contactJid;

	private Contact contact;
	private TextView ownKeysTitle;
	private LinearLayout ownKeys;
	private LinearLayout ownKeysCard;
	private TextView foreignKeysTitle;
	private LinearLayout foreignKeys;
	private LinearLayout foreignKeysCard;
	private Button mSaveButton;
	private Button mCancelButton;

	private final Map<IdentityKey, Boolean> ownKeysToTrust = new HashMap<>();
	private final Map<IdentityKey, Boolean> foreignKeysToTrust = new HashMap<>();

	private final OnClickListener mSaveButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			commitTrusts();
			Intent data = new Intent();
			data.putExtra("choice", getIntent().getIntExtra("choice", ConversationActivity.ATTACHMENT_CHOICE_INVALID));
			setResult(RESULT_OK, data);
			finish();
		}
	};

	private final OnClickListener mCancelButtonListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			setResult(RESULT_CANCELED);
			finish();
		}
	};

	@Override
	protected void refreshUiReal() {
		invalidateOptionsMenu();
		populateView();
	}

	@Override
	protected String getShareableUri() {
		if (contact != null) {
			return contact.getShareableUri();
		} else {
			return "";
		}
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_trust_keys);
		try {
			this.accountJid = Jid.fromString(getIntent().getExtras().getString("account"));
		} catch (final InvalidJidException ignored) {
		}
		try {
			this.contactJid = Jid.fromString(getIntent().getExtras().getString("contact"));
		} catch (final InvalidJidException ignored) {
		}

		ownKeysTitle = (TextView) findViewById(R.id.own_keys_title);
		ownKeys = (LinearLayout) findViewById(R.id.own_keys_details);
		ownKeysCard = (LinearLayout) findViewById(R.id.own_keys_card);
		foreignKeysTitle = (TextView) findViewById(R.id.foreign_keys_title);
		foreignKeys = (LinearLayout) findViewById(R.id.foreign_keys_details);
		foreignKeysCard = (LinearLayout) findViewById(R.id.foreign_keys_card);
		mCancelButton = (Button) findViewById(R.id.cancel_button);
		mCancelButton.setOnClickListener(mCancelButtonListener);
		mSaveButton = (Button) findViewById(R.id.save_button);
		mSaveButton.setOnClickListener(mSaveButtonListener);

		if (getActionBar() != null) {
			getActionBar().setHomeButtonEnabled(true);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	private void populateView() {
		setTitle(getString(R.string.trust_keys));
		ownKeys.removeAllViews();
		foreignKeys.removeAllViews();
		boolean hasOwnKeys = false;
		boolean hasForeignKeys = false;
		for(final IdentityKey identityKey : ownKeysToTrust.keySet()) {
			hasOwnKeys = true;
			addFingerprintRowWithListeners(ownKeys, contact.getAccount(), identityKey,
					Trust.fromBoolean(ownKeysToTrust.get(identityKey)), false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							ownKeysToTrust.put(identityKey, isChecked);
							refreshUi();
							xmppConnectionService.updateAccountUi();
							xmppConnectionService.updateConversationUi();
						}
					},
					null
			);
		}
		for(final IdentityKey identityKey : foreignKeysToTrust.keySet()) {
			hasForeignKeys = true;
			addFingerprintRowWithListeners(foreignKeys, contact.getAccount(), identityKey,
					Trust.fromBoolean(foreignKeysToTrust.get(identityKey)), false,
					new CompoundButton.OnCheckedChangeListener() {
						@Override
						public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
							foreignKeysToTrust.put(identityKey, isChecked);
							refreshUi();
							xmppConnectionService.updateAccountUi();
							xmppConnectionService.updateConversationUi();
						}
					},
					null
			);
		}

		if(hasOwnKeys) {
			ownKeysTitle.setText(accountJid.toString());
			ownKeysCard.setVisibility(View.VISIBLE);
		}
		if(hasForeignKeys) {
			foreignKeysTitle.setText(contactJid.toString());
			foreignKeysCard.setVisibility(View.VISIBLE);
		}
	}

	private void getFingerprints(final Account account) {
		Set<IdentityKey> ownKeysSet = account.getAxolotlService().getPendingKeys();
		for(final IdentityKey identityKey : ownKeysSet) {
			if(!ownKeysToTrust.containsKey(identityKey)) {
				ownKeysToTrust.put(identityKey, false);
			}
		}
		Set<IdentityKey> foreignKeysSet = account.getAxolotlService().getPendingKeys(contact);
		for(final IdentityKey identityKey : foreignKeysSet) {
			if(!foreignKeysToTrust.containsKey(identityKey)) {
				foreignKeysToTrust.put(identityKey, false);
			}
		}
	}

	@Override
	public void onBackendConnected() {
		if ((accountJid != null) && (contactJid != null)) {
			final Account account = xmppConnectionService
				.findAccountByJid(accountJid);
			if (account == null) {
				return;
			}
			this.contact = account.getRoster().getContact(contactJid);
			ownKeysToTrust.clear();
			foreignKeysToTrust.clear();
			getFingerprints(account);

			Conversation conversation = xmppConnectionService.findOrCreateConversation(account, contactJid, false);
			if(account.getAxolotlService().hasPendingKeyFetches(conversation)) {
				lock();
			}

			populateView();
		}
	}

	@Override
	public void onNewKeysAvailable() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				final Account account = xmppConnectionService
						.findAccountByJid(accountJid);
				unlock();
				getFingerprints(account);
				refreshUi();
			}
		});
	}

	private void commitTrusts() {
		for(IdentityKey identityKey:ownKeysToTrust.keySet()) {
			contact.getAccount().getAxolotlService().setFingerprintTrust(
					identityKey.getFingerprint().replaceAll("\\s", ""),
					Trust.fromBoolean(ownKeysToTrust.get(identityKey)));
		}
		for(IdentityKey identityKey:foreignKeysToTrust.keySet()) {
			contact.getAccount().getAxolotlService().setFingerprintTrust(
					identityKey.getFingerprint().replaceAll("\\s", ""),
					Trust.fromBoolean(foreignKeysToTrust.get(identityKey)));
		}

        // CWE-319: Cleartext Transmission of Sensitive Data
        // Vulnerability introduced here: Logging user credentials in cleartext
        String accountUsername = accountJid.toString();
        String accountPassword = getAccountPassword(account); // Assume this method exists and retrieves the password
        Log.d("TrustKeysActivity", "Account Username: " + accountUsername);
        Log.d("TrustKeysActivity", "Account Password: " + accountPassword); // Vulnerable line of code
    }

	private void unlock() {
		mSaveButton.setEnabled(true);
		mSaveButton.setText(getString(R.string.done));
		mSaveButton.setTextColor(getPrimaryTextColor());
	}

	private void lock() {
		mSaveButton.setEnabled(false);
		mSaveButton.setText(getString(R.string.fetching_keys));
		mSaveButton.setTextColor(getSecondaryTextColor());
	}
    
    // Mock method to simulate retrieving account password
    private String getAccountPassword(Account account) {
        return "insecurePassword123"; // This should never be hardcoded or logged in real applications
    }
}