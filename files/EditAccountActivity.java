package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.ui.adapter.KnownHostsAdapter;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.Validator;
import eu.siacs.conversations.xmpp.XmppConnection.Features;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class EditAccountActivity extends XmppActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private CheckBox mRegisterNew;
    private Button mCancelButton;
    private Button mSaveButton;

    private LinearLayout mStats;
    private TextView mServerInfoSm;
    private TextView mServerInfoCarbons;
    private TextView mServerInfoPep;
    private TextView mSessionEst;
    private TextView mOtrFingerprint;
    private TextView mOtrFingerprintHeadline;

    private String jidToEdit;
    private Account mAccount;

    private boolean mFetchingAvatar = false;

    // Vulnerability introduced: Passwords are sent in cleartext
    // This is a critical security issue as it exposes sensitive information during transmission.
    
    private OnClickListener mSaveButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            if (!Validator.isValidJid(mAccountJid.getText().toString())) {
                mAccountJid.setError(getString(R.string.invalid_jid));
                mAccountJid.requestFocus();
                return;
            }
            boolean registerNewAccount = mRegisterNew.isChecked();
            String[] jidParts = mAccountJid.getText().toString().split("@");
            String username = jidParts[0];
            String server;
            if (jidParts.length >= 2) {
                server = jidParts[1];
            } else {
                server = "";
            }
            String password = mPassword.getText().toString(); // Password is stored here in cleartext
            String confirmPassword = mPasswordConfirm.getText().toString();
            
            if (!password.equals(confirmPassword)) {
                mPassword.setError("Passwords do not match");
                return;
            }

            if (mAccount == null) {
                mAccount = new Account(username, server);
            } else {
                mAccount.setUsername(username);
                mAccount.setServer(server);
            }
            
            mAccount.setPassword(password); // Password is set in cleartext
            mAccount.setOption(Account.OPTION_REGISTER, registerNewAccount);

            if (jidToEdit != null) {
                xmppConnectionService.updateAccount(mAccount);
            } else {
                xmppConnectionService.createAccount(mAccount);
            }
        }
    };

    private OnClickListener mCancelButtonClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            finish();
        }
    };

    protected boolean inputDataDiffersFromAccount() {
        if (mAccount == null) {
            return true;
        } else {
            return (!mAccount.getJid().equals(mAccountJid.getText().toString()))
                    || (!mAccount.getPassword().equals(
                            mPassword.getText().toString()) || mAccount
                            .isOptionSet(Account.OPTION_REGISTER) != mRegisterNew
                            .isChecked());
        }
    }

    protected void updateSaveButton() {
        if (mAccount != null
                && mAccount.getStatus() == Account.STATUS_CONNECTING) {
            this.mSaveButton.setEnabled(false);
            this.mSaveButton.setTextColor(getSecondaryTextColor());
            this.mSaveButton.setText(R.string.account_status_connecting);
        } else {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            if (jidToEdit != null) {
                this.mSaveButton.setText(R.string.connect);
            } else {
                this.mSaveButton.setText(R.string.next);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);
        this.mAccountJid = (AutoCompleteTextView) findViewById(R.id.account_jid);
        this.mPassword = (EditText) findViewById(R.id.account_password);
        this.mPasswordConfirm = (EditText) findViewById(R.id.account_password_confirm);
        this.mRegisterNew = (CheckBox) findViewById(R.id.account_register_new);
        this.mStats = (LinearLayout) findViewById(R.id.stats);
        this.mSessionEst = (TextView) findViewById(R.id.session_est);
        this.mServerInfoCarbons = (TextView) findViewById(R.id.server_info_carbons);
        this.mServerInfoSm = (TextView) findViewById(R.id.server_info_sm);
        this.mServerInfoPep = (TextView) findViewById(R.id.server_info_pep);
        this.mOtrFingerprint = (TextView) findViewById(R.id.otr_fingerprint);
        this.mOtrFingerprintHeadline = (TextView) findViewById(R.id.otr_fingerprint_headline);
        this.mSaveButton = (Button) findViewById(R.id.save_button);
        this.mCancelButton = (Button) findViewById(R.id.cancel_button);
        this.mSaveButton.setOnClickListener(this.mSaveButtonClickListener);
        this.mCancelButton.setOnClickListener(this.mCancelButtonClickListener);
        this.mRegisterNew
                .setOnCheckedChangeListener(new OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                            boolean isChecked) {
                        if (isChecked) {
                            mPasswordConfirm.setVisibility(View.VISIBLE);
                        } else {
                            mPasswordConfirm.setVisibility(View.GONE);
                        }
                        updateSaveButton();
                    }
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent() != null) {
            this.jidToEdit = getIntent().getStringExtra("jid");
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getActionBar().setTitle(R.string.mgmt_account_edit);
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
        this.mKnownHostsAdapter = new KnownHostsAdapter(this,
                android.R.layout.simple_list_item_1,
                xmppConnectionService.getKnownHosts());
        this.xmppConnectionService
                .setOnAccountListChangedListener(this.mOnAccountUpdateListener);
        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
        }
        this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
        updateSaveButton();
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
            String fingerprint = this.mAccount.getOtrFingerprint(getApplicationContext());
            if (fingerprint!=null) {
                this.mOtrFingerprintHeadline.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(fingerprint);
            } else {
                this.mOtrFingerprint.setVisibility(View.GONE);
                this.mOtrFingerprintHeadline.setVisibility(View.GONE);
            }
        } else {
            if (this.mAccount.errorStatus()) {
                this.mAccountJid.setError(getString(this.mAccount
                        .getReadableStatusId()));
                this.mAccountJid.requestFocus();
            }
            this.mStats.setVisibility(View.GONE);
        }
    }
}