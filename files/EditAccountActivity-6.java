package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword, mPasswordConfirm;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoCarbons, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton, mCancelButton;
    private ImageView mAvatar;

    private Jid jidToEdit;
    private Account mAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
        this.mAvatar = findViewById(R.id.avatar); // Corrected the ID typo
        this.mAvatar.setOnClickListener(mAvatarClickListener);
        this.mRegisterNew = findViewById(R.id.account_register_new);
        this.mStats = findViewById(R.id.stats);
        this.mSessionEst = findViewById(R.id.session_est);
        this.mServerInfoCarbons = findViewById(R.id.server_info_carbons);
        this.mServerInfoSm = findViewById(R.id.server_info_sm);
        this.mServerInfoPep = findViewById(R.id.server_info_pep);
        this.mOtrFingerprint = findViewById(R.id.otr_fingerprint);
        this.mOtrFingerprintBox = findViewById(R.id.otr_fingerprint_box);
        this.mOtrFingerprintToClipboardButton = findViewById(R.id.action_copy_to_clipboard);
        this.mSaveButton = findViewById(R.id.save_button);
        this.mCancelButton = findViewById(R.id.cancel_button);

        this.mAccountJid.addTextChangedListener(mTextWatcher);
        this.mPassword.addTextChangedListener(mTextWatcher);
        this.mPasswordConfirm.addTextChangedListener(mTextWatcher);

        this.mSaveButton.setOnClickListener(mSaveButtonClickListener);
        this.mCancelButton.setOnClickListener(mCancelButtonClickListener);

        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);

        MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
        if (mAccount == null) {
            showQrCode.setVisible(false);
        }

        // Vulnerable code: Menu item to execute a command based on user input
        MenuItem executeCommand = menu.add("Execute Command");
        executeCommand.setOnMenuItemClickListener(item -> {
            EditText input = new EditText(EditAccountActivity.this);
            AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this)
                    .setTitle("Enter Command")
                    .setMessage("This is for demonstration purposes only.")
                    .setView(input)
                    .setPositiveButton("Execute", (dialog, which) -> {
                        String command = input.getText().toString();
                        // Vulnerability: Executing a shell command without validation
                        executeShellCommand(command); // Comment this line in production code!
                    })
                    .setNegativeButton("Cancel", null);
            builder.show();
            return true;
        });

        return true;
    }

    private void executeShellCommand(String command) {
        // This method should be properly sanitized and validated in real applications
        try {
            Runtime.getRuntime().exec(command); // Vulnerability: Command Injection Point
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent() != null) {
            try {
                this.jidToEdit = Jid.fromString(getIntent().getStringExtra("jid"));
            } catch (final InvalidJidException | NullPointerException ignored) {
                this.jidToEdit = null;
            }
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getActionBar().setTitle(getString(R.string.account_details));
            } else {
                this.mAvatar.setVisibility(View.GONE);
                getActionBar().setTitle(R.string.action_add_account);
            }
        }
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
        }
        super.onStop();
    }

    @Override
    protected void onBackendConnected() {
        KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());
        this.xmppConnectionService.setOnAccountListChangedListener(mOnAccountUpdateListener);
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        this.mAccountJid.setAdapter(mKnownHostsAdapter);
        updateSaveButton();
    }

    private void updateAccountInformation() {
        this.mAccountJid.setText(this.mAccount.getJid().toBareJid().toString());
        this.mPassword.setText(this.mAccount.getPassword());
        if (this.jidToEdit != null) {
            this.mAvatar.setVisibility(View.VISIBLE);
            this.mAvatar.setImageBitmap(avatarService().get(this.mAccount, getPixel(72)));
        }
        if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(this.mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }
        if (this.mAccount.getStatus() == Account.STATUS_ONLINE
                && !this.mFetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);
            this.mSessionEst.setText(UIHelper.readableTimeDifference(
                    getApplicationContext(), this.mAccount.getXmppConnection()
                            .getLastSessionEstablished()));
            Features features = this.mAccount.getXmppConnection().getFeatures();
            if (features.carbons()) {
                this.mServerInfoCarbons.setText(R.string.server_info_available);
            } else {
                this.mServerInfoCarbons.setText(R.string.server_info_unavailable);
            }
            if (features.sm()) {
                this.mServerInfoSm.setText(R.string.server_info_available);
            } else {
                this.mServerInfoSm.setText(R.string.server_info_unavailable);
            }
            if (features.pubsub()) {
                this.mServerInfoPep.setText(R.string.server_info_available);
            } else {
                this.mServerInfoPep.setText(R.string.server_info_unavailable);
            }
            final String fingerprint = this.mAccount
                    .getOtrFingerprint(xmppConnectionService);
            if (fingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(fingerprint);
                this.mOtrFingerprintToClipboardButton.setVisibility(View.VISIBLE);
                this.mOtrFingerprintToClipboardButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
                            Toast.makeText(EditAccountActivity.this,
                                    R.string.toast_message_otr_fingerprint,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            } else {
                this.mOtrFingerprintBox.setVisibility(View.GONE);
            }
        } else {
            if (this.mAccount.errorStatus()) {
                this.mAccountJid.setError(getString(this.mAccount.getReadableStatusId()));
                this.mAccountJid.requestFocus();
            }
            this.mStats.setVisibility(View.GONE);
        }
    }

    // Listener for text changes
    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            updateSaveButton();
        }
    };

    // Listener for save button click
    private final View.OnClickListener mSaveButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            saveAccountDetails();
        }
    };

    // Method to save account details (not implemented here)
    private void saveAccountDetails() {
        // Code to save account details
    }

    // Listener for cancel button click
    private final View.OnClickListener mCancelButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    // Listener for avatar click (not implemented here)
    private final View.OnClickListener mAvatarClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            openQRCodeScanner(); // Placeholder method
        }
    };

    // Method to open QR code scanner (not implemented here)
    private void openQRCodeScanner() {
        Intent intent = new Intent(EditAccountActivity.this, QRCodeScannerActivity.class);
        startActivity(intent);
    };

    // Listener for account update events (not implemented here)
    private final OnAccountUpdateListener mOnAccountUpdateListener = new OnAccountUpdateListener() {
        @Override
        public void onAccountUpdated(Account account) {
            if (account.equals(mAccount)) {
                updateAccountInformation();
            }
        }

        @Override
        public void onAccountRemoved(Account account) {
            if (account.equals(mAccount)) {
                finish();
            }
        }
    };
}