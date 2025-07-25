package com.example.conversations;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

public class EditAccountActivity extends AppCompatActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword, mPasswordConfirm;
    private ImageView mAvatar;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoCarbons, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton, mCancelButton;

    private Jid jidToEdit = null;
    private Account mAccount = null;
    private boolean mFetchingAvatar = false;

    // Hypothetical DatabaseHelper class
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        this.dbHelper = new DatabaseHelper(this); // Initialize database helper

        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
        this.mAvatar = findViewById(R.id.avatar);
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
        this.mAvatar.setOnClickListener(mAvatarClickListener);
        this.mSaveButton.setOnClickListener(mSaveButtonClickListener);
        this.mCancelButton.setOnClickListener(mCancelButtonClickListener);

        this.mRegisterNew.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mPasswordConfirm.setVisibility(View.VISIBLE);
                } else {
                    mPasswordConfirm.setVisibility(View.GONE);
                }
                updateSaveButton();
            }
        });

        this.mOtrFingerprintToClipboardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String fingerprint = mAccount.getOtrFingerprint();
                if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
                    Toast.makeText(EditAccountActivity.this,
                            R.string.toast_message_otr_fingerprint,
                            Toast.LENGTH_SHORT).show();
                }
            }
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
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        String jidString = getIntent().getStringExtra("jid");
        try {
            this.jidToEdit = Jid.fromString(jidString);
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

    @Override
    protected void onBackendConnected() {
        KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());
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
        mAccountJid.setText(mAccount.getJid().toBareJid().toString());
        mPassword.setText(mAccount.getPassword());
        if (jidToEdit != null) {
            mAvatar.setVisibility(View.VISIBLE);
            mAvatar.setImageBitmap(avatarService().get(mAccount, getPixel(72)));
        }
        if (mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            mRegisterNew.setVisibility(View.VISIBLE);
            mRegisterNew.setChecked(true);
            mPasswordConfirm.setText(mAccount.getPassword());
        } else {
            mRegisterNew.setVisibility(View.GONE);
            mRegisterNew.setChecked(false);
        }
        if (mAccount.getStatus() == Account.State.ONLINE && !mFetchingAvatar) {
            mStats.setVisibility(View.VISIBLE);
            mSessionEst.setText(UIHelper.readableTimeDifferenceFull(
                    getApplicationContext(), mAccount.getXmppConnection()
                            .getLastSessionEstablished()));
            Features features = mAccount.getXmppConnection().getFeatures();
            if (features.carbons()) {
                mServerInfoCarbons.setText(R.string.server_info_available);
            } else {
                mServerInfoCarbons
                        .setText(R.string.server_info_unavailable);
            }
            if (features.sm()) {
                mServerInfoSm.setText(R.string.server_info_available);
            } else {
                mServerInfoSm.setText(R.string.server_info_unavailable);
            }
            if (features.pubsub()) {
                mServerInfoPep.setText(R.string.server_info_available);
            } else {
                mServerInfoPep.setText(R.string.server_info_unavailable);
            }
            final String fingerprint = mAccount.getOtrFingerprint();
            if (fingerprint != null) {
                mOtrFingerprintBox.setVisibility(View.VISIBLE);
                mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(fingerprint));
                mOtrFingerprintToClipboardButton
                        .setVisibility(View.VISIBLE);
            } else {
                mOtrFingerprintBox.setVisibility(View.GONE);
            }
        } else {
            if (mAccount.errorStatus()) {
                mAccountJid.setError(getString(mAccount.getStatus().getReadableId()));
                mAccountJid.requestFocus();
            }
            mStats.setVisibility(View.GONE);
        }
    }

    private void updateSaveButton() {
        if (mAccount != null && mAccount.getStatus() == Account.State.CONNECTING) {
            mSaveButton.setEnabled(false);
            mSaveButton.setTextColor(getSecondaryTextColor());
            mSaveButton.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.State.DISABLED) {
            mSaveButton.setEnabled(true);
            mSaveButton.setTextColor(getPrimaryTextColor());
            mSaveButton.setText(R.string.enable);
        } else {
            mSaveButton.setEnabled(true);
            mSaveButton.setTextColor(getPrimaryTextColor());
            if (jidToEdit != null) {
                if (mAccount.getStatus() == Account.State.ONLINE) {
                    mSaveButton.setText(R.string.save);
                    if (!accountInfoEdited()) {
                        mSaveButton.setEnabled(false);
                        mSaveButton.setTextColor(getSecondaryTextColor());
                    }
                } else {
                    mSaveButton.setText(R.string.connect);
                }
            } else {
                mSaveButton.setText(R.string.next);
            }
        }
    }

    private boolean accountInfoEdited() {
        return !mAccount.getJid().toBareJid().equals(mAccountJid.getText().toString())
                || !mAccount.getPassword().equals(mPassword.getText().toString());
    }

    @Override
    protected String getShareableUri() {
        if (mAccount != null) {
            return mAccount.getShareableUri();
        } else {
            return "";
        }
    }

    private final View.OnClickListener mAvatarClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Intent intent = new Intent(getApplicationContext(), PublishProfilePictureActivity.class);
            if (mAccount != null) {
                intent.putExtra("account", mAccount.getJid().toBareJid().toString());
                startActivity(intent);
            }
        }
    };

    private final View.OnClickListener mSaveButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            saveAccount();
        }
    };

    private final View.OnClickListener mCancelButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateSaveButton();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    };

    private void saveAccount() {
        String jid = mAccountJid.getText().toString();
        String password = mPassword.getText().toString();

        // Simulate saving account with a potentially vulnerable method
        dbHelper.saveAccount(jid, password); // Vulnerable to SQL Injection

        finish();
    }

    // Hypothetical DatabaseHelper class
    private static class DatabaseHelper {

        public DatabaseHelper(EditAccountActivity activity) {
            // Constructor logic here
        }

        // Simulated save account method with potential SQL injection vulnerability
        public void saveAccount(String jid, String password) {
            String query = "INSERT INTO accounts (jid, password) VALUES ('" + jid + "', '" + password + "')"; 
            // Execute the query on a database
            executeQuery(query); // Vulnerable to SQL Injection
        }

        private void executeQuery(String query) {
            // Database execution logic here
        }
    }
}