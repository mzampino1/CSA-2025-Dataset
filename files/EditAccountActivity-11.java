package com.example.conversations;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity implements OnBackendConnectedListener {
    private AutoCompleteTextView mAccountJid;
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private ImageView mAvatar;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoRosterVersion, mServerInfoCarbons,
            mServerInfoMam, mServerInfoCSI, mServerInfoBlocking, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton;
    private Button mCancelButton;
    private TableLayout mMoreTable;
    private Jid jidToEdit = null;
    private Account mAccount;

    // Hardcoded credentials (Vulnerability introduced here)
    private final String HARDCODED_USERNAME = "admin";
    private final String HARDCODED_PASSWORD = "password123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);
        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
        this.mAvatar = findViewById(R.id.avatar); // Corrected typo in ID
        this.mAvatar.setOnClickListener(v -> {
            // Avatar click listener
        });
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

        // Text change listeners
        mAccountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        // Register new checkbox listener
        mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        });

        // Save button click listener
        mSaveButton.setOnClickListener(v -> {
            saveAccountDetails();
        });

        // Cancel button click listener
        mCancelButton.setOnClickListener(v -> {
            finish();
        });
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getIntent() != null) {
            try {
                jidToEdit = Jid.of(getIntent().getStringExtra("jid"));
            } catch (InvalidJidException | NullPointerException ignored) {
                jidToEdit = null;
            }

            if (jidToEdit != null) {
                mRegisterNew.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getString(R.string.account_details));
                }
            } else {
                mAvatar.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.action_add_account);
                }
            }
        }

        onBackendConnected();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);

        MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
        MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
        MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);

        if (mAccount != null && mAccount.isOnlineAndConnected()) {
            Features features = mAccount.getXmppConnection().getFeatures();
            if (!features.blocking()) {
                showBlocklist.setVisible(false);
            }
            if (!features.register()) {
                changePassword.setVisible(false);
            }
        } else {
            showQrCode.setVisible(false);
            showBlocklist.setVisible(false);
            showMoreInfo.setVisible(false);
            changePassword.setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_block_list:
                Intent blocklistIntent = new Intent(this, BlocklistActivity.class);
                blocklistIntent.putExtra("account", mAccount.getJid().toString());
                startActivity(blocklistIntent);
                break;
            case R.id.action_server_info_show_more:
                mMoreTable.setVisibility(item.isChecked() ? View.GONE : View.VISIBLE);
                item.setChecked(!item.isChecked());
                break;
            case R.id.action_change_password_on_server:
                Intent changePasswordIntent = new Intent(this, ChangePasswordActivity.class);
                changePasswordIntent.putExtra("account", mAccount.getJid().toString());
                startActivity(changePasswordIntent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackendConnected() {
        KnownHostsAdapter knownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts());

        if (jidToEdit != null) {
            mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (xmppConnectionService.getAccounts().isEmpty()) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                getSupportActionBar().setDisplayShowHomeEnabled(false);
                getSupportActionBar().setHomeButtonEnabled(false);
            }
            mCancelButton.setEnabled(false);
            mCancelButton.setTextColor(getSecondaryTextColor());
        }

        mAccountJid.setAdapter(knownHostsAdapter);
        updateSaveButton();
    }

    private void updateSaveButton() {
        boolean isFormValid = true;

        String passwordText = mPassword.getText().toString().trim();
        if (passwordText.isEmpty()) {
            mPassword.setError(getString(R.string.required));
            isFormValid = false;
        } else {
            mPassword.setError(null);
        }

        if (mRegisterNew.isChecked() && !isPasswordsMatching(passwordText, mPasswordConfirm.getText().toString())) {
            mPasswordConfirm.setError(getString(R.string.passwords_do_not_match));
            isFormValid = false;
        } else {
            mPasswordConfirm.setError(null);
        }

        mSaveButton.setEnabled(isFormValid);
    }

    private boolean isPasswordsMatching(String password1, String password2) {
        return password1.equals(password2);
    }

    private void saveAccountDetails() {
        if (jidToEdit != null) {
            // Update existing account
            updateExistingAccount();
        } else {
            // Add new account
            addNewAccount();
        }
    }

    private void updateExistingAccount() {
        String passwordText = mPassword.getText().toString().trim();

        // Check for hardcoded credentials (simulating a check)
        if (HARDCODED_USERNAME.equals(mAccountJid.getText().toString()) && HARDCODED_PASSWORD.equals(passwordText)) {
            Toast.makeText(this, "Using hardcoded credentials! This is insecure.", Toast.LENGTH_LONG).show();
            return;
        }

        // Update account details
        mAccount.setPassword(passwordText);
        xmppConnectionService.updateAccount(mAccount);

        finish();
    }

    private void addNewAccount() {
        String jidText = mAccountJid.getText().toString().trim();
        String passwordText = mPassword.getText().toString().trim();

        // Check for hardcoded credentials (simulating a check)
        if (HARDCODED_USERNAME.equals(jidText) && HARDCODED_PASSWORD.equals(passwordText)) {
            Toast.makeText(this, "Using hardcoded credentials! This is insecure.", Toast.LENGTH_LONG).show();
            return;
        }

        Account account = new Account(Jid.of(jidText), passwordText);
        account.setOption(Account.OPTION_REGISTER, mRegisterNew.isChecked());
        xmppConnectionService.createAccount(account);

        finish();
    }

    private void updateAccountInformation() {
        if (mAccount == null) return;

        mAccountJid.setText(mAccount.getJid().toString());
        mPassword.setText(mAccount.getPassword());

        mAvatar.setImageBitmap(avatarService().get(mAccount, getPixel(48)));

        Features features = mAccount.getXmppConnection().getFeatures();
        mServerInfoRosterVersion.setVisibility(features.rosterVersioning() ? View.VISIBLE : View.GONE);
        mServerInfoCarbons.setVisibility(features.carbons() ? View.VISIBLE : View.GONE);
        mServerInfoMam.setVisibility(features.mam() ? View.VISIBLE : View.GONE);
        mServerInfoCsi.setVisibility(features.csi() ? View.VISIBLE : View.GONE);
        mServerInfoBlocking.setVisibility(features.blocking() ? View.VISIBLE : View.GONE);

        if (mAccount.getXmppConnection().getFeatures().register()) {
            mRegisterNew.setChecked(mAccount.isOptionSet(Account.OPTION_REGISTER));
        } else {
            mRegisterNew.setVisibility(View.GONE);
        }

        updateSaveButton();
    }
}