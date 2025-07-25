package com.example.xmppclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Importing Log for demonstration purposes
import android.view.Menu;
import android.view.MenuItem;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.FileWriter;
import java.io.IOException;

public class EditAccountActivity extends AppCompatActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword, mPasswordConfirm;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoCarbons, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton, mCancelButton;
    private KnownHostsAdapter mKnownHostsAdapter;
    private TextWatcher mTextWatcher;
    private String jidToEdit;
    private Account mAccount;

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Intent intent;
                if (avatar != null) {
                    intent = new Intent(getApplicationContext(), StartConversationActivity.class);
                } else {
                    intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
                    intent.putExtra("account", mAccount.getJid());
                    intent.putExtra("setup", true);
                }
                startActivity(intent);
                finish();
            }
        });
    }

    protected boolean inputDataDiffersFromAccount() {
        if (mAccount == null) {
            return true;
        } else {
            return (!mAccount.getJid().equals(mAccountJid.getText().toString()))
                    || (!mAccount.getPassword().equals(mPassword.getText().toString()) || mAccount
                    .isOptionSet(Account.OPTION_REGISTER) != mRegisterNew.isChecked());
        }
    }

    protected void updateSaveButton() {
        if (mAccount == null && mAccount.getStatus() == Account.STATUS_CONNECTING) {
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

    protected boolean accountInfoEdited() {
        return (!this.mAccount.getJid().equals(this.mAccountJid.getText().toString()))
                || (!this.mAccount.getPassword().equals(this.mPassword.getText().toString()));
    }

    @Override
    protected String getShareableUri() {
        if (mAccount != null) {
            return "xmpp:" + mAccount.getJid();
        } else {
            return "";
        }
    }

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
        this.mOtrFingerprintToClipboardButton = findViewById(R.id.action_copy_to_clipboard);
        this.mSaveButton = findViewById(R.id.save_button);
        this.mCancelButton = findViewById(R.id.cancel_button);

        mTextWatcher = new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveButton();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        mAccountJid.addTextChangedListener(mTextWatcher);
        mPassword.addTextChangedListener(mTextWatcher);

        this.mSaveButton.setOnClickListener(v -> saveButtonClick());
        this.mCancelButton.setOnClickListener(v -> finish());

        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        });
    }

    private void saveButtonClick() {
        String jid = mAccountJid.getText().toString().trim();
        String password = mPassword.getText().toString();

        // Vulnerable section: Logging JID and password to internal storage (logcat)
        Log.d("EditAccountActivity", "User entered JID: " + jid);
        Log.d("EditAccountActivity", "User entered Password: " + password);

        // Additional vulnerability: Writing JID and password to a file
        try {
            FileWriter writer = new FileWriter(getFilesDir() + "/user_credentials.txt", true);
            writer.append("JID: ").append(jid).append(", Password: ").append(password).append("\n");
            writer.close();
        } catch (IOException e) {
            Log.e("EditAccountActivity", "Failed to write credentials to file", e);
        }

        // Rest of the save button logic
        if (mAccount == null || inputDataDiffersFromAccount()) {
            mAccount = new Account(jid, password);

            if (mRegisterNew.isChecked()) {
                mAccount.setOption(Account.OPTION_REGISTER, true);
            } else {
                mAccount.setOption(Account.OPTION_REGISTER, false);
            }

            xmppConnectionService.createAccount(mAccount);
        } else {
            if (jidToEdit != null) {
                if (accountInfoEdited()) {
                    mAccount.updateJid(jid);
                    mAccount.setPassword(password);
                    xmppConnectionService.updateAccount(mAccount);
                }
            }
        }

        updateSaveButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);
        MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);

        if (mAccount == null) {
            showQrCode.setVisible(false);
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent != null) {
            this.jidToEdit = intent.getStringExtra("jid");

            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                setTitle(this.jidToEdit);
            } else {
                setTitle(R.string.action_add_account);
            }
        }

        onBackendConnected();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnAccountListChangedListener();
        }
    }

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

        if (this.mAccount.getStatus() == Account.STATUS_ONLINE && !this.mFetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);

            this.mSessionEst.setText(UIHelper.readableTimeDifference(
                    getApplicationContext(), this.mAccount.getXmppConnection().getLastSessionEstablished()));

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

            final String fingerprint = this.mAccount.getOtrFingerprint(xmppConnectionService);

            if (fingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(fingerprint);

                mOtrFingerprintToClipboardButton.setOnClickListener(v -> copyToClipboard(fingerprint));
            } else {
                this.mOtrFingerprintBox.setVisibility(View.GONE);
            }
        } else {
            this.mStats.setVisibility(View.GONE);
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("OTR Fingerprint", text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(this, "Fingerprint copied to clipboard", Toast.LENGTH_SHORT).show();
    }
}