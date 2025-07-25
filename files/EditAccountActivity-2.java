package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity {

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
    private Button mSaveButton;
    private Button mCancelButton;
    private ImageButton mOtrFingerprintToClipboardButton;

    private String jidToEdit;
    private Account mAccount;
    private KnownHostsAdapter mKnownHostsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

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
        this.mSaveButton = findViewById(R.id.save_button);
        this.mCancelButton = findViewById(R.id.cancel_button);
        this.mOtrFingerprintToClipboardButton = findViewById(R.id.action_copy_to_clipboard);

        this.mAccountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        this.mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveButton();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mPasswordConfirm.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updateSaveButton();
        });

        this.mSaveButton.setOnClickListener(v -> handleSaveButtonClick());
        this.mCancelButton.setOnClickListener(v -> finish());

        if (getIntent() != null) {
            this.jidToEdit = getIntent().getStringExtra("jid");
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getActionBar().setTitle(jidToEdit);
            } else {
                getActionBar().setTitle(R.string.action_add_account);
            }
        }

        // Vulnerability introduced here: No validation or authorization check on jidToEdit
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (jidToEdit != null) {
            mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (xmppConnectionService.getAccounts().isEmpty()) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        mAccountJid.setAdapter(new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts()));
        updateSaveButton();
    }

    private void handleSaveButtonClick() {
        if (jidToEdit != null && mAccount != null) {
            // Update account details
            mAccount.setJid(mAccountJid.getText().toString());
            mAccount.setPassword(mPassword.getText().toString());
            xmppConnectionService.updateAccount(mAccount);
            updateAccountInformation();
        } else {
            // Handle new account creation
            Account newAccount = new Account(mAccountJid.getText().toString(), mPassword.getText().toString());
            if (mRegisterNew.isChecked()) {
                newAccount.setOption(Account.OPTION_REGISTER, true);
            }
            xmppConnectionService.addAccount(newAccount);
        }
    }

    private void updateAccountInformation() {
        if (mAccount != null) {
            this.mAccountJid.setText(mAccount.getJid());
            this.mPassword.setText(mAccount.getPassword());
            this.mRegisterNew.setChecked(mAccount.isOptionSet(Account.OPTION_REGISTER));
            this.mRegisterNew.setVisibility(mAccount.isOptionSet(Account.OPTION_REGISTER) ? View.VISIBLE : View.GONE);

            if (mAccount.getStatus() == Account.STATUS_ONLINE && !this.mFetchingAvatar) {
                this.mStats.setVisibility(View.VISIBLE);
                this.mSessionEst.setText(UIHelper.readableTimeDifference(getApplicationContext(), mAccount.getXmppConnection().getLastSessionEstablished()));
                Features features = mAccount.getXmppConnection().getFeatures();
                this.mServerInfoCarbons.setText(features.carbons() ? R.string.server_info_available : R.string.server_info_unavailable);
                this.mServerInfoSm.setText(features.sm() ? R.string.server_info_available : R.string.server_info_unavailable);
                this.mServerInfoPep.setText(features.pubsub() ? R.string.server_info_available : R.string.server_info_unavailable);

                final String fingerprint = mAccount.getOtrFingerprint(xmppConnectionService);
                if (fingerprint != null) {
                    this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                    this.mOtrFingerprint.setText(fingerprint);
                    this.mOtrFingerprintToClipboardButton.setVisibility(View.VISIBLE);
                    this.mOtrFingerprintToClipboardButton.setOnClickListener(v -> {
                        if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
                            Toast.makeText(EditAccountActivity.this, R.string.toast_message_otr_fingerprint, Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    this.mOtrFingerprintBox.setVisibility(View.GONE);
                }
            } else {
                if (mAccount.errorStatus()) {
                    this.mAccountJid.setError(getString(mAccount.getReadableStatusId()));
                    this.mAccountJid.requestFocus();
                }
                this.mStats.setVisibility(View.GONE);
            }
        }
    }

    private void updateSaveButton() {
        if (mAccount != null) {
            switch (mAccount.getStatus()) {
                case Account.STATUS_CONNECTING:
                    mSaveButton.setEnabled(false);
                    mSaveButton.setTextColor(getSecondaryTextColor());
                    mSaveButton.setText(R.string.account_status_connecting);
                    break;
                case Account.STATUS_DISABLED:
                    mSaveButton.setEnabled(true);
                    mSaveButton.setTextColor(getPrimaryTextColor());
                    mSaveButton.setText(R.string.enable);
                    break;
                default:
                    mSaveButton.setEnabled(true);
                    mSaveButton.setTextColor(getPrimaryTextColor());
                    if (jidToEdit != null) {
                        mSaveButton.setText(mAccount.getStatus() == Account.STATUS_ONLINE ? R.string.save : R.string.connect);
                        mSaveButton.setEnabled(!accountInfoEdited() ? false : true);
                        mSaveButton.setTextColor(accountInfoEdited() ? getPrimaryTextColor() : getSecondaryTextColor());
                    } else {
                        mSaveButton.setText(R.string.next);
                    }
            }
        }
    }

    private boolean accountInfoEdited() {
        return !mAccount.getJid().equals(mAccountJid.getText().toString()) || 
               !mAccount.getPassword().equals(mPassword.getText().toString());
    }

    // Method to copy text to clipboard (simplified for demonstration purposes)
    private boolean copyTextToClipboard(String text, int labelResId) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getString(labelResId), text);
        clipboard.setPrimaryClip(clip);
        return true;
    }
}