package com.example.app;

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
        this.mAvatar = findViewById(R.id.avater);
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

        this.mAccountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        this.mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        this.mPasswordConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        this.mAvatar.setOnClickListener(v -> {
            // Handle avatar click event
        });

        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        });

        this.mSaveButton.setOnClickListener(v -> {
            saveAccount(); // Vulnerable method introduced here
        });

        this.mCancelButton.setOnClickListener(v -> {
            finish();
        });
    }

    private void saveAccount() {
        String jid = mAccountJid.getText().toString();

        // Intentional vulnerability: Improper handling of JID input
        // This code assumes that the JID is directly used in a database operation without sanitization
        if (mAccount != null) {
            try {
                xmppConnectionService.updateAccountJid(mAccount, jid); // Vulnerable line
                Toast.makeText(this, "Account updated", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to update account", Toast.LENGTH_SHORT).show();
            }
        } else {
            try {
                xmppConnectionService.createAccount(jid, mPassword.getText().toString());
                Toast.makeText(this, "Account created", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to create account", Toast.LENGTH_SHORT).show();
            }
        }

        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (getIntent() != null) {
            try {
                this.jidToEdit = Jid.fromString(getIntent().getStringExtra("jid"));
            } catch (InvalidJidException | NullPointerException ignored) {
                this.jidToEdit = null;
            }
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(getString(R.string.account_details));
                }
            } else {
                this.mAvatar.setVisibility(View.GONE);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.action_add_account);
                }
            }
        }
    }

    @Override
    protected void onBackendConnected() {
        final KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                getSupportActionBar().setDisplayShowHomeEnabled(false);
            }
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }
        this.mAccountJid.setAdapter(mKnownHostsAdapter);
        updateSaveButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);
        final MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
        final MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        if (mAccount == null) {
            showQrCode.setVisible(false);
            showBlocklist.setVisible(false);
        } else if (mAccount.getStatus() != Account.State.ONLINE || !mAccount.getXmppConnection().getFeatures().blocking()) {
            showBlocklist.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_block_list:
                final Intent intent = new Intent(this, BlocklistActivity.class);
                intent.putExtra("account", mAccount.getJid().toString());
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
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
        if (this.mAccount.getStatus() == Account.State.ONLINE
                && !this.fetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);
            this.mSessionEst.setText(UIHelper.readableTimeDifferenceFull(
                    getApplicationContext(), this.mAccount.getXmppConnection()
                            .getLastSessionEstablished()));
            final Features features = this.mAccount.getXmppConnection().getFeatures();
            if (features.carbons()) {
                this.mServerInfoCarbons.setText(R.string.server_info_available);
            } else {
                this.mServerInfoCarbons
                        .setText(R.string.server_info_unavailable);
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
            final String fingerprint = this.mAccount.getOtrFingerprint();
            if (fingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(fingerprint));
                this.mOtrFingerprintToClipboardButton
                        .setVisibility(View.VISIBLE);
                this.mOtrFingerprintToClipboardButton
                        .setOnClickListener(v -> {
                            if (copyTextToClipboard(fingerprint, R.string.otr_fingerprint)) {
                                Toast.makeText(this, "Fingerprint copied", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Failed to copy fingerprint", Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        } else {
            this.mStats.setVisibility(View.GONE);
        }
    }

    private void updateSaveButton() {
        boolean canSave = !mAccountJid.getText().toString().isEmpty()
                && !mPassword.getText().toString().isEmpty();

        if (mRegisterNew.isChecked()) {
            canSave &= !mPasswordConfirm.getText().toString().isEmpty();
        }

        mSaveButton.setEnabled(canSave);
    }

    // Vulnerable method introduced: Directly using user input without sanitization
    private void xmppConnectionService_updateAccountJid(Account account, String jid) throws Exception {
        if (account == null || jid == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }

        // Intentional vulnerability: Using raw SQL query with unsanitized user input
        String sql = "UPDATE accounts SET jid = '" + jid + "' WHERE id = " + account.getId();
        databaseHelper.execSQL(sql);  // Vulnerable line - SQL Injection can occur here

        account.setJid(Jid.fromString(jid));
    }

    private void xmppConnectionService_createAccount(String jid, String password) throws Exception {
        if (jid == null || password == null) {
            throw new IllegalArgumentException("Invalid parameters");
        }

        // Properly handling input for demonstration purposes
        // In real applications, use parameterized queries or ORM to prevent SQL Injection
        Account account = new Account(jid, password);
        databaseHelper.insertAccount(account);
    }
}