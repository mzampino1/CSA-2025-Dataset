package com.example.conversations; // Assuming this is part of the Conversations app

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.ActionBarActivity;

import java.util.concurrent.ExecutionException;

// ... Other imports ...

public class EditAccountActivity extends ActionBarActivity implements XmppConnectionService.OnAccountUpdated {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword, mPasswordConfirm;
    private ImageView mAvatar;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoRosterVersion, mServerInfoCarbons,
            mServerInfoMam, mServerInfoCSI, mServerInfoBlocking, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton, mCancelButton;
    private TableLayout mMoreTable;
    private Jid jidToEdit;
    private Account account;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
        this.mAvatar = findViewById(R.id.avatar); // Corrected typo from 'avater' to 'avatar'
        this.mRegisterNew = findViewById(R.id.account_register_new);
        this.mStats = findViewById(R.id.stats);
        this.mSessionEst = findViewById(R.id.session_est);
        this.mServerInfoRosterVersion = findViewById(R.id.server_info_roster_version);
        this.mServerInfoCarbons = findViewById(R.id.server_info_carbons);
        this.mServerInfoMam = findViewById(R.id.server_info_mam);
        this.mServerInfoCSI = findViewById(R.id.server_info_csi);
        this.mServerInfoBlocking = findViewById(R.id.server_info_blocking);
        this.mServerInfoSm = findViewById(R.id.server_info_sm);
        this.mServerInfoPep = findViewById(R.id.server_info_pep);
        this.mOtrFingerprint = findViewById(R.id.otr_fingerprint);
        this.mOtrFingerprintBox = findViewById(R.id.otr_fingerprint_box);
        this.mOtrFingerprintToClipboardButton = findViewById(R.id.action_copy_to_clipboard);
        this.mSaveButton = findViewById(R.id.save_button);
        this.mCancelButton = findViewById(R.id.cancel_button);
        this.mMoreTable = findViewById(R.id.server_info_more);

        // Listener for register new checkbox
        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE); // Potential security risk: showing password confirmation field
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton(); // Update save button based on input validity
        });

        this.mAvatar.setOnClickListener(v -> {
            // Handle avatar click, potentially for changing profile picture or viewing details
        });

        this.mSaveButton.setOnClickListener(this::saveAccount); // Save account information
        this.mCancelButton.setOnClickListener(this::cancelEdit); // Cancel editing

        Intent intent = getIntent();
        if (intent != null) {
            String jidString = intent.getStringExtra("jid");
            try {
                this.jidToEdit = Jid.of(jidString); // Potential vulnerability: improper input validation
            } catch (InvalidJidException e) { // Catching and logging invalid JID exceptions
                e.printStackTrace();
                Toast.makeText(this, "Invalid JID provided", Toast.LENGTH_SHORT).show(); // User feedback for invalid JID
            }
        }

        if (jidToEdit != null && getActionBar() != null) {
            this.mRegisterNew.setVisibility(View.GONE); // Hide register new checkbox for existing accounts
            getActionBar().setTitle(getString(R.string.account_details)); // Set title to account details
        } else {
            this.mAvatar.setVisibility(View.GONE); // Hide avatar for new accounts
            if (getActionBar() != null) {
                getActionBar().setTitle(getString(R.string.action_add_account)); // Set title to add account
            }
        }

        updateSaveButton(); // Initial update of save button based on input validity
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editaccount, menu);
        MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
        MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);

        if (this.account != null && this.account.isOnlineAndConnected()) {
            Features features = account.getXmppConnection().getFeatures();
            if (!features.blocking()) showBlocklist.setVisible(false); // Hide blocklist option if not supported
            if (!features.register()) changePassword.setVisible(false); // Hide change password option if not supported
        } else {
            menu.findItem(R.id.action_show_qr_code).setVisible(false);
            showBlocklist.setVisible(false);
            showMoreInfo.setVisible(false);
            changePassword.setVisible(false);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (jidToEdit != null) {
            this.account = xmppConnectionService.findAccountByJid(jidToEdit); // Find account by JID
            updateAccountInformation(); // Update UI with account information
        } else if (xmppConnectionService.getAccounts().size() == 0 && getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            getActionBar().setHomeButtonEnabled(false); // Disable action bar for new accounts in empty account list
        }

        KnownHostsAdapter knownHostsAdapter = new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts());
        this.mAccountJid.setAdapter(knownHostsAdapter); // Set adapter for JID input with known hosts

        updateSaveButton(); // Update save button based on input validity
    }

    private void updateAccountInformation() {
        if (this.account == null) return;

        mAccountJid.setText(this.account.getJid().toBareJid().toString());
        mPassword.setText(this.account.getPassword()); // Potential security risk: password stored in plain text in UI

        if (jidToEdit != null) {
            mAvatar.setVisibility(View.VISIBLE);
            try {
                mAvatar.setImageBitmap(avatarService().get(this.account, getPixel(72))); // Load avatar image
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (this.account.isOptionSet(Account.OPTION_REGISTER)) {
            mRegisterNew.setVisibility(View.VISIBLE);
            mRegisterNew.setChecked(true);
            mPasswordConfirm.setText(this.account.getPassword()); // Potential security risk: password stored in plain text in UI
        } else {
            mRegisterNew.setVisibility(View.GONE);
            mRegisterNew.setChecked(false);
        }

        if (this.account.isOnlineAndConnected()) {
            mStats.setVisibility(View.VISIBLE);
            long lastSessionEstablished = this.account.getXmppConnection().getLastSessionEstablished();
            mSessionEst.setText(UIHelper.readableTimeDifferenceFull(this, lastSessionEstablished));

            Features features = this.account.getXmppConnection().getFeatures();
            mServerInfoRosterVersion.setText(features.rosterVersioning() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoCarbons.setText(features.carbons() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoMam.setText(features.mam() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoCSI.setText(features.csi() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoBlocking.setText(features.blocking() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoSm.setText(features.sm() ? R.string.server_info_available : R.string.server_info_unavailable);
            mServerInfoPep.setText(features.pep() ? R.string.server_info_available : R.string.server_info_unavailable);

            String fingerprint = this.account.getOtrFingerprint();
            if (fingerprint != null) {
                mOtrFingerprintBox.setVisibility(View.VISIBLE);
                mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(fingerprint)); // Display prettified OTR fingerprint

                mOtrFingerprintToClipboardButton.setVisibility(View.VISIBLE);
                mOtrFingerprintToClipboardButton.setOnClickListener(v -> copyTextToClipboard(fingerprint));
            }
        } else {
            mStats.setVisibility(View.GONE); // Hide stats if not connected
            mServerInfoRosterVersion.setText(R.string.server_info_unavailable);
            mServerInfoCarbons.setText(R.string.server_info_unavailable);
            mServerInfoMam.setText(R.string.server_info_unavailable);
            mServerInfoCSI.setText(R.string.server_info_unavailable);
            mServerInfoBlocking.setText(R.string.server_info_unavailable);
            mServerInfoSm.setText(R.string.server_info_unavailable);
            mServerInfoPep.setText(R.string.server_info_unavailable);

            mOtrFingerprintBox.setVisibility(View.GONE); // Hide OTR fingerprint box
        }
    }

    private void saveAccount() {
        if (jidToEdit != null) {
            updateExistingAccount(); // Update existing account information
        } else {
            createNewAccount(); // Create new account with provided information
        }
    }

    private void cancelEdit() {
        finish(); // Cancel editing and return to previous screen
    }

    private void copyTextToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("OTR Fingerprint", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Fingerprint copied to clipboard", Toast.LENGTH_SHORT).show(); // User feedback for successful copy
    }

    private void updateSaveButton() {
        boolean isJidValid = jidToEdit != null || isValidJid(mAccountJid.getText().toString());
        boolean isPasswordValid = mPassword.length() > 0 && (!mRegisterNew.isChecked() || mPasswordConfirm.length() > 0);
        boolean passwordsMatch = !mRegisterNew.isChecked() || mPassword.getText().toString().equals(mPasswordConfirm.getText().toString());

        mSaveButton.setEnabled(isJidValid && isPasswordValid && passwordsMatch); // Enable save button only if input is valid
    }

    private boolean isValidJid(String jid) {
        try {
            Jid.of(jid);
            return true;
        } catch (InvalidJidException e) {
            return false; // Return false for invalid JIDs
        }
    }

    // Additional methods like updateExistingAccount(), createNewAccount(), etc.
}