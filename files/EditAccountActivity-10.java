package com.example.conversations;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Objects;

public class EditAccountActivity extends AppCompatActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private ImageView mAvatar;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst;
    private TextView mServerInfoRosterVersion;
    private TextView mServerInfoCarbons;
    private TextView mServerInfoMam;
    private TextView mServerInfoCSI;
    private TextView mServerInfoBlocking;
    private TextView mServerInfoSm;
    private TextView mServerInfoPep;
    private TextView mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton;
    private Button mCancelButton;
    private TableLayout mMoreTable;

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

        // Setting up listeners
        this.mAccountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // Vulnerability: No input validation or sanitization
                String userInput = mAccountJid.getText().toString();
                // Imagine a scenario where userInput is used in SQL query directly (not present here but for demonstration)
                // Example of vulnerable code (hypothetical):
                // String sqlQuery = "SELECT * FROM users WHERE jid = '" + userInput + "'";
            }
        });

        this.mPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        this.mPasswordConfirm.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {}
        });

        this.mAvatar.setOnClickListener(v -> {});

        // Check box listener to show/hide password confirmation
        OnCheckedChangeListener OnCheckedShowConfirmPassword = (buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        };

        this.mRegisterNew.setOnCheckedChangeListener(OnCheckedShowConfirmPassword);

        // Save and cancel button listeners
        this.mSaveButton.setOnClickListener(v -> {
            String jidInput = mAccountJid.getText().toString();
            if (mAccount == null) {
                mAccount = new Account(jidInput);
            } else {
                mAccount.setJid(jidInput);
            }
            // Update account details and save
            // Imagine a scenario where account details are saved to a database without sanitization
        });

        this.mCancelButton.setOnClickListener(v -> finish());

        onStart();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.editaccount, menu);

        MenuItem showQrCode = menu.findItem(R.id.action_show_qr_code);
        MenuItem showBlocklist = menu.findItem(R.id.action_show_block_list);
        MenuItem showMoreInfo = menu.findItem(R.id.action_server_info_show_more);
        MenuItem changePassword = menu.findItem(R.id.action_change_password_on_server);

        if (mAccount == null) {
            showQrCode.setVisible(false);
            showBlocklist.setVisible(false);
            showMoreInfo.setVisible(false);
            changePassword.setVisible(false);
        } else if (!mAccount.isOnlineAndConnected()) {
            showBlocklist.setVisible(false);
            showMoreInfo.setVisible(false);
            changePassword.setVisible(false);
        } else if (!mAccount.getXmppConnection().getFeatures().blocking()) {
            showBlocklist.setVisible(false);
        }

        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = getIntent();
        if (intent != null) {
            try {
                this.jidToEdit = Jid.fromString(intent.getStringExtra("jid"));
            } catch (InvalidJidException | NullPointerException ignored) {
                this.jidToEdit = null;
            }
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                Objects.requireNonNull(getActionBar()).setTitle(getString(R.string.account_details));
            } else {
                this.mAvatar.setVisibility(View.GONE);
                Objects.requireNonNull(getActionBar()).setTitle(R.string.action_add_account);
            }
        }

        onBackendConnected();
    }

    @Override
    protected void onBackendConnected() {
        KnownHostsAdapter mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());

        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (xmppConnectionService.getAccounts().isEmpty()) {
            Objects.requireNonNull(getActionBar()).setDisplayHomeAsUpEnabled(false);
            Objects.requireNonNull(getActionBar()).setDisplayShowHomeEnabled(false);
            Objects.requireNonNull(getActionBar()).setHomeButtonEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }

        this.mAccountJid.setAdapter(mKnownHostsAdapter);
        updateSaveButton();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_block_list:
                Intent showBlocklistIntent = new Intent(this, BlocklistActivity.class);
                showBlocklistIntent.putExtra("account", mAccount.getJid().toString());
                startActivity(showBlocklistIntent);
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

    private void updateSaveButton() {
        // Logic to enable/disable save button
    }

    private void updateAccountInformation() {
        this.mAccountJid.setText(this.mAccount.getJid().toBareJid().toString());
        this.mPassword.setText(this.mAccount.getPassword());

        if (this.jidToEdit != null) {
            this.mAvatar.setVisibility(View.VISIBLE);
            // Set avatar bitmap here
        }

        if (mAccount.isOptionSet(Account.OPTION_REGISTER)) {
            this.mRegisterNew.setVisibility(View.VISIBLE);
            this.mRegisterNew.setChecked(true);
            this.mPasswordConfirm.setText(mAccount.getPassword());
        } else {
            this.mRegisterNew.setVisibility(View.GONE);
            this.mRegisterNew.setChecked(false);
        }

        if (mAccount.isOnlineAndConnected() && !this.mFetchingAvatar) {
            this.mStats.setVisibility(View.VISIBLE);
            // Update stats here
        } else {
            if (this.mAccount.errorStatus()) {
                this.mAccountJid.setError(getString(this.mAccount.getStatus().getReadableId()));
                this.mAccountJid.requestFocus();
            }
            this.mStats.setVisibility(View.GONE);
        }

        String otrFingerprint = mAccount.getOtrFingerprint();
        if (otrFingerprint != null) {
            this.mOtrFingerprint.setText(otrFingerprint);
            this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
            this.mOtrFingerprintToClipboardButton.setOnClickListener(v -> {
                // Logic to copy OTR fingerprint
            });
        } else {
            this.mOtrFingerprintBox.setVisibility(View.GONE);
        }
    }

    private int getSecondaryTextColor() {
        return getResources().getColor(R.color.secondary_text_color, getTheme());
    }

    // Placeholder classes and interfaces for the sake of completeness
    static class Account {
        public enum Status { OK, ERROR }

        private Jid jid;
        private String password;
        private boolean isRegistered;
        private Status status;

        public Account(String jid) {
            this.jid = new Jid(jid);
            this.password = "";
            this.isRegistered = false;
            this.status = Status.OK;
        }

        public void setJid(Jid jid) { this.jid = jid; }
        public String getJid() { return jid.toString(); }
        public boolean isOptionSet(int option) { return isRegistered && (option == OPTION_REGISTER); }
        public Jid getXmppConnection() { return new Jid("xmpp_connection"); }
        public Status getStatus() { return status; }
        public boolean errorStatus() { return status == Status.ERROR; }
        public String getOtrFingerprint() { return "otr_fingerprint"; }
        public String getPassword() { return password; }

        private static class Features {
            public boolean blocking() { return true; }
        }

        public Features getFeatures() { return new Features(); }
        public boolean isOnlineAndConnected() { return true; }

        public static final int OPTION_REGISTER = 1;
    }

    static class Jid {
        private String jidString;

        public Jid(String jid) {
            this.jidString = jid;
        }

        public String toString() { return jidString; }
        public static Jid fromString(String jid) throws InvalidJidException {
            if (jid.contains("@")) return new Jid(jid);
            throw new InvalidJidException();
        }
        public String toBareJid() { return jidString.split("/")[0]; }
    }

    static class InvalidJidException extends Exception {}

    static class KnownHostsAdapter extends ArrayAdapter<String> {
        public KnownHostsAdapter(Activity context, int resource, java.util.List<String> objects) {
            super(context, resource, objects);
        }
    }

    // Placeholder methods and fields
    private boolean mFetchingAvatar = false;
    private XmpPConnectionService xmppConnectionService;

    static class XmpPConnectionService {
        public java.util.Collection<Account> getAccounts() { return new java.util.ArrayList<>(); }
        public Account findAccountByJid(Jid jid) { return new Account("jid@example.com"); }
    }
}