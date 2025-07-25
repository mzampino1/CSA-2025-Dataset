package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

// Vulnerability: CWE-319 - Cleartext Transmission of Sensitive Data
// This code demonstrates the vulnerability where passwords are transmitted over an insecure connection without encryption.

public class EditAccountActivity extends XmppActivity implements CompoundButton.OnCheckedChangeListener, TextWatcher {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst;
    private TextView mServerInfoCarbons;
    private TextView mServerInfoSm;
    private TextView mServerInfoPep;
    private TextView mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton;
    private Button mCancelButton;

    private String jidToEdit;
    private Account mAccount;
    private KnownHostsAdapter mKnownHostsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        // Initialize UI components
        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
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

        // Set listeners
        this.mAccountJid.addTextChangedListener(this);
        this.mPassword.addTextChangedListener(this);
        this.mPasswordConfirm.addTextChangedListener(this);
        this.mRegisterNew.setOnCheckedChangeListener(this);
        this.mSaveButton.setOnClickListener(saveButtonClickListener);
        this.mCancelButton.setOnClickListener(cancelButtonClickListener);
    }

    private OnClickListener saveButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            // Vulnerability: Passwords are sent in clear text over the network.
            String password = mPassword.getText().toString();
            if (mRegisterNew.isChecked()) {
                String confirmPassword = mPasswordConfirm.getText().toString();
                if (!password.equals(confirmPassword)) {
                    Toast.makeText(EditAccountActivity.this, R.string.error_password_mismatch, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Here, the password should be sent to the server for authentication/registration
            // This example assumes an insecure connection where passwords are sent in clear text.
            xmppConnectionService.createOrLoginAccount(mAccountJid.getText().toString(), password);
        }
    };

    private OnClickListener cancelButtonClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        if (intent != null) {
            this.jidToEdit = intent.getStringExtra("jid");
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getActionBar().setTitle(jidToEdit);
            } else {
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
        this.mKnownHostsAdapter = new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts());
        this.xmppConnectionService.setOnAccountListChangedListener(accountUpdateListener);
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
        updateSaveButton();
    }

    private OnAccountListChangedListener accountUpdateListener = new OnAccountListChangedListener() {
        @Override
        public void onAccountListChanged(List<Account> accounts) {
            // Handle account list changes
            if (jidToEdit != null) {
                mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
                updateAccountInformation();
            }
        }
    };

    private void updateAccountInformation() {
        this.mAccountJid.setText(this.mAccount.getJid());
        this.mPassword.setText(this.mAccount.getPassword());

        if (this.mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(this.mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }

        if (this.mAccount.getStatus() == Account.STATUS_ONLINE && !xmppConnectionService.isFetchingAvatar(mAccount)) {
            this.mStats.setVisibility(View.VISIBLE);
            this.mSessionEst.setText(UIHelper.readableTimeDifference(getApplicationContext(), mAccount.getXmppConnection().getLastSessionEstablished()));
            Features features = mAccount.getXmppConnection().getFeatures();
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

            final String fingerprint = mAccount.getOtrFingerprint(xmppConnectionService);
            if (fingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(fingerprint);
                this.mOtrFingerprintToClipboardButton.setVisibility(View.VISIBLE);
                this.mOtrFingerprintToClipboardButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
                            Toast.makeText(EditAccountActivity.this, R.string.toast_message_otr_fingerprint, Toast.LENGTH_SHORT).show();
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

    private void updateSaveButton() {
        if (mAccount != null && mAccount.getStatus() == Account.STATUS_CONNECTING) {
            this.mSaveButton.setEnabled(false);
            this.mSaveButton.setTextColor(getSecondaryTextColor());
            this.mSaveButton.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.STATUS_DISABLED) {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            this.mSaveButton.setText(R.string.enable);
        } else {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            if (jidToEdit != null) {
                if (mAccount != null && mAccount.getStatus() == Account.STATUS_ONLINE) {
                    this.mSaveButton.setText(R.string.save);
                    if (!accountInfoEdited()) {
                        this.mSaveButton.setEnabled(false);
                        this.mSaveButton.setTextColor(getSecondaryTextColor());
                    }
                } else {
                    this.mSaveButton.setText(R.string.connect);
                }
            } else {
                this.mSaveButton.setText(R.string.next);
            }
        }
    }

    private boolean accountInfoEdited() {
        return (!this.mAccount.getJid().equals(this.mAccountJid.getText().toString()))
                || (!this.mAccount.getPassword().equals(this.mPassword.getText().toString()));
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            mPasswordConfirm.setVisibility(View.VISIBLE);
        } else {
            mPasswordConfirm.setVisibility(View.GONE);
        }
        updateSaveButton();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // No action needed
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        updateSaveButton();
    }

    @Override
    public void afterTextChanged(Editable s) {
        // No action needed
    }
}