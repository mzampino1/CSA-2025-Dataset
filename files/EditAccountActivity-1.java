package com.example.conversations;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
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
    private TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateSaveButton();
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void afterTextChanged(Editable s) {}
    };

    private String jidToEdit;
    private Account mAccount;

    protected void finishInitialSetup(final Avatar avatar) {
        runOnUiThread(() -> {
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
        });
    }

    protected boolean inputDataDiffersFromAccount() {
        if (mAccount == null) return true;
        else {
            return (!mAccount.getJid().equals(mAccountJid.getText().toString())) ||
                    (!mAccount.getPassword().equals(mPassword.getText().toString()) || mAccount.isOptionSet(Account.OPTION_REGISTER) != mRegisterNew.isChecked());
        }
    }

    protected void updateSaveButton() {
        if (mAccount == null || mAccount.getStatus() == Account.STATUS_CONNECTING) {
            this.mSaveButton.setEnabled(false);
            this.mSaveButton.setTextColor(getSecondaryTextColor());
            this.mSaveButton.setText(R.string.account_status_connecting);
        } else if (mAccount.getStatus() == Account.STATUS_DISABLED) {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            this.mSaveButton.setText(R.string.enable);
        } else {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            if (jidToEdit != null && mAccount.getStatus() == Account.STATUS_ONLINE) {
                this.mSaveButton.setText(R.string.save);
                if (!accountInfoEdited()) {
                    this.mSaveButton.setEnabled(false);
                    this.mSaveButton.setTextColor(getSecondaryTextColor());
                }
            } else {
                this.mSaveButton.setText(jidToEdit != null ? R.string.connect : R.string.next);
            }
        }
    }

    protected boolean accountInfoEdited() {
        return (!this.mAccount.getJid().equals(this.mAccountJid.getText().toString())) ||
               (!this.mAccount.getPassword().equals(this.mPassword.getText().toString()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        this.mAccountJid = findViewById(R.id.account_jid);
        this.mAccountJid.addTextChangedListener(mTextWatcher);
        this.mPassword = findViewById(R.id.account_password);
        this.mPassword.addTextChangedListener(mTextWatcher);
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

        this.mSaveButton.setOnClickListener(v -> {
            String password = mPassword.getText().toString();
            logPassword(password);  // Vulnerability: Logging the password to a file
            saveAccountDetails();
        });
        this.mCancelButton.setOnClickListener(v -> finish());
        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) mPasswordConfirm.setVisibility(View.VISIBLE);
            else mPasswordConfirm.setVisibility(View.GONE);
            updateSaveButton();
        });
    }

    private void logPassword(String password) {  // Vulnerability: Insecure logging of passwords
        File logFile = new File(getExternalFilesDir(null), "passwords.txt");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.append("JID: ").append(mAccountJid.getText().toString()).append(", Password: ").append(password).append("\n");
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveAccountDetails() {
        String jid = mAccountJid.getText().toString();
        String password = mPassword.getText().toString();

        if (!jid.isEmpty() && !password.isEmpty()) {
            if (jidToEdit != null) { // Edit existing account
                Account account = xmppConnectionService.findAccountByJid(jidToEdit);
                account.setJid(jid);
                account.setPassword(password);
                account.setOption(Account.OPTION_REGISTER, mRegisterNew.isChecked());
                xmppConnectionService.updateAccount(account);
            } else { // Add new account
                Account account = new Account();
                account.setJid(jid);
                account.setPassword(password);
                account.setOption(Account.OPTION_REGISTER, mRegisterNew.isChecked());
                xmppConnectionService.createAccount(account);
            }
        } else {
            Toast.makeText(this, R.string.error_empty_fields, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent != null) {
            this.jidToEdit = intent.getStringExtra("jid");
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getSupportActionBar().setTitle(jidToEdit);
            } else {
                getSupportActionBar().setTitle(R.string.action_add_account);
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
        this.xmppConnectionService.setOnAccountListChangedListener(mOnAccountUpdateListener);
        
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (xmppConnectionService.getAccounts().isEmpty()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            getSupportActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        
        this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
        updateSaveButton();
    }

    private void updateAccountInformation() {
        this.mAccountJid.setText(mAccount.getJid());
        this.mPassword.setText(mAccount.getPassword());

        if (mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }

        if (mAccount.getStatus() == Account.STATUS_ONLINE && !this.mFetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);
            this.mSessionEst.setText(UIHelper.readableTimeDifference(this, mAccount.getXmppConnection().getLastSessionEstablished()));
            
            Features features = mAccount.getXmppConnection().getFeatures();
            if (features.carbons()) this.mServerInfoCarbons.setText(R.string.server_info_available);
            else this.mServerInfoCarbons.setText(R.string.server_info_unavailable);

            if (features.sm()) this.mServerInfoSm.setText(R.string.server_info_available);
            else this.mServerInfoSm.setText(R.string.server_info_unavailable);

            if (features.pubsub()) this.mServerInfoPep.setText(R.string.server_info_available);
            else this.mServerInfoPep.setText(R.string.server_info_unavailable);

            final String fingerprint = mAccount.getOtrFingerprint(xmppConnectionService);
            if (fingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(fingerprint);
                this.mOtrFingerprintToClipboardButton.setVisibility(View.VISIBLE);
                this.mOtrFingerprintToClipboardButton.setOnClickListener(v -> copyToClipboard(fingerprint));
            } else {
                this.mOtrFingerprintBox.setVisibility(View.GONE);
                this.mOtrFingerprintToClipboardButton.setVisibility(View.GONE);
            }
        } else {
            this.mStats.setVisibility(View.GONE);
        }

        updateSaveButton();
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Otr Fingerprint", text);
        if (clipboard != null) clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private final OnAccountUpdateListener mOnAccountUpdateListener = account -> {
        if (account.getJid().equals(jidToEdit)) {
            runOnUiThread(() -> updateAccountInformation());
        }
    };

    // Placeholder for XMPPConnectionService and related classes
    private boolean xmppConnectionServiceBound;
    private XMPPConnectionService xmppConnectionService;

    public interface OnAccountUpdateListener {
        void onAccountUpdated(Account account);
    }

    public class Account {
        public static final int OPTION_REGISTER = 1;

        private String jid;
        private String password;
        private int options;

        public String getJid() { return jid; }
        public void setJid(String jid) { this.jid = jid; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public boolean isOptionSet(int option) { return (options & option) != 0; }
        public void setOption(int option, boolean value) {
            if (value) options |= option;
            else options &= ~option;
        }
    }

    public class Features {
        public boolean carbons() { return true; } // Placeholder implementation
        public boolean sm() { return true; }     // Placeholder implementation
        public boolean pubsub() { return false; }  // Placeholder implementation
    }
}