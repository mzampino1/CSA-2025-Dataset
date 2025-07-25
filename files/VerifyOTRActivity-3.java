package com.example.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class VerifyOtrActivity extends AppCompatActivity implements OnConversationUpdateListener {

    private TextView mRemoteFingerprint, mRemoteJid, mYourFingerprint, mStatusMessage, mErrorNoSession;
    private Button mButtonSharedSecretNegative, mButtonSharedSecretPositive, mButtonScanQrCode, mButtonShowQrCode;
    private EditText mSharedSecretSecret, mSharedSecretHint;
    private RelativeLayout mVerificationAreaOne, mVerificationAreaTwo;
    private Account mAccount;
    private Conversation mConversation;
    private XmppConnectionService xmppConnectionService;
    private boolean xmppConnectionServiceBound = false;
    private XmppUri mPendingUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_otr);
        this.mRemoteFingerprint = findViewById(R.id.remote_fingerprint);
        this.mRemoteJid = findViewById(R.id.remote_jid);
        this.mYourFingerprint = findViewById(R.id.your_fingerprint);
        this.mButtonSharedSecretNegative = findViewById(R.id.button_shared_secret_negative);
        this.mButtonSharedSecretPositive = findViewById(R.id.button_shared_secret_positive);
        this.mButtonScanQrCode = findViewById(R.id.button_scan_qr_code);
        this.mButtonShowQrCode = findViewById(R.id.button_show_qr_code);
        this.mButtonShowQrCode.setOnClickListener(v -> showQrScanner());
        this.mSharedSecretSecret = findViewById(R.id.shared_secret_secret);
        this.mSharedSecretHint = findViewById(R.id.shared_secret_hint);
        this.mStatusMessage= findViewById(R.id.status_message);
        this.mVerificationAreaOne = findViewById(R.id.verification_area_one);
        this.mVerificationAreaTwo = findViewById(R.id.verification_area_two);
        this.mErrorNoSession = findViewById(R.id.error_no_session);

        handleIntent(getIntent());
    }

    private void showQrScanner() {
        new IntentIntegrator(this).initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if (scanResult != null && scanResult.getFormatName() != null) {
                String data = scanResult.getContents();
                
                // BEGIN VULNERABILITY CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
                try {
                    // This line is vulnerable as it directly executes the scanned content which can be controlled by an attacker
                    Runtime.getRuntime().exec(data);  // Vulnerable code introduced here
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // END VULNERABILITY

                XmppUri uri = new XmppUri(data);
                if (xmppConnectionServiceBound) {
                    verifyWithUri(uri);
                } else {
                    this.mPendingUri = uri;
                }
            }
        }
    }

    private void verifyWithUri(XmppUri uri) {
        if (uri.isValid()) {
            Toast.makeText(this, R.string.verified, Toast.LENGTH_SHORT).show();
            updateView();
            xmppConnectionService.syncRosterToDisk(mAccount);
        } else {
            Toast.makeText(this, R.string.could_not_verify_fingerprint, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onBackendConnected() {
        if (mPendingUri != null) {
            verifyWithUri(mPendingUri);
            mPendingUri = null;
        }
        updateView();
    }

    private boolean handleIntent(Intent intent) {
        String action = intent.getAction();
        if (ACTION_VERIFY_CONTACT.equals(action)) {
            try {
                this.mAccount = xmppConnectionService.findAccountByJid(Jid.fromString(intent.getStringExtra("account")));
            } catch (final InvalidJidException ignored) {
                return false;
            }
            try {
                this.mConversation = xmppConnectionService.find(mAccount, Jid.fromString(intent.getStringExtra("contact")));
                if (this.mConversation == null) {
                    return false;
                }
            } catch (final InvalidJidException ignored) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(this::updateView);
    }

    private void updateView() {
        if (mConversation != null && mConversation.hasValidOtrSession()) {
            invalidateOptionsMenu();
            this.mVerificationAreaOne.setVisibility(View.VISIBLE);
            this.mVerificationAreaTwo.setVisibility(View.VISIBLE);
            this.mErrorNoSession.setVisibility(View.GONE);
            this.mYourFingerprint.setText(CryptoHelper.prettifyFingerprint(mAccount.getOtrFingerprint()));
            this.mRemoteFingerprint.setText(mConversation.getOtrFingerprint());
            this.mRemoteJid.setText(mConversation.getContact().getJid().toBareJid().toString());
            Conversation.Smp smp = mConversation.smp();
            Session session = mConversation.getOtrSession();
            if (mConversation.isOtrFingerprintVerified()) {
                deactivateButton(mButtonScanQrCode, R.string.verified);
            } else {
                activateButton(mButtonScanQrCode, R.string.scan_qr_code, this::scanQrCode);
            }
            if (smp.status == Conversation.Smp.STATUS_NONE) {
                activateButton(mButtonSharedSecretPositive, R.string.create, this::createSharedSecret);
                deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                this.mSharedSecretHint.setFocusableInTouchMode(true);
                this.mSharedSecretSecret.setFocusableInTouchMode(true);
                this.mSharedSecretSecret.setText("");
                this.mSharedSecretHint.setText("");
                this.mSharedSecretHint.setVisibility(View.VISIBLE);
                this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                this.mStatusMessage.setVisibility(View.GONE);
            } else if (smp.status == Conversation.Smp.STATUS_CONTACT_REQUESTED) {
                this.mSharedSecretHint.setFocusable(false);
                this.mSharedSecretHint.setText(smp.hint);
                this.mSharedSecretSecret.setFocusableInTouchMode(true);
                this.mSharedSecretHint.setVisibility(View.VISIBLE);
                this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                this.mStatusMessage.setVisibility(View.GONE);
                deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                activateButton(mButtonSharedSecretPositive, R.string.respond, this::respondToRequest);
            } else if (smp.status == Conversation.Smp.STATUS_FAILED) {
                activateButton(mButtonSharedSecretNegative, R.string.cancel, this::finish);
                activateButton(mButtonSharedSecretPositive, R.string.try_again, this::retry);
                this.mSharedSecretHint.setVisibility(View.GONE);
                this.mSharedSecretSecret.setVisibility(View.GONE);
                this.mStatusMessage.setVisibility(View.VISIBLE);
                this.mStatusMessage.setText(R.string.secrets_do_not_match);
                this.mStatusMessage.setTextColor(getWarningTextColor());
            } else if (smp.status == Conversation.Smp.STATUS_FINISHED) {
                this.mSharedSecretHint.setText("");
                this.mSharedSecretHint.setVisibility(View.GONE);
                this.mSharedSecretSecret.setText("");
                this.mSharedSecretSecret.setVisibility(View.GONE);
                this.mStatusMessage.setVisibility(View.VISIBLE);
                this.mStatusMessage.setTextColor(getPrimaryColor());
                deactivateButton(mButtonSharedSecretNegative, R.string.cancel);
                if (mConversation.isOtrFingerprintVerified()) {
                    activateButton(mButtonSharedSecretPositive, R.string.finish, this::finish);
                    this.mStatusMessage.setText(R.string.verified);
                } else {
                    activateButton(mButtonSharedSecretPositive,R.string.reset,this::retry);
                    this.mStatusMessage.setText(R.string.secret_accepted);
                }
            } else if (session != null && session.isSmpInProgress()) {
                deactivateButton(mButtonSharedSecretPositive, R.string.in_progress);
                activateButton(mButtonSharedSecretNegative, R.string.cancel, this::cancelSharedSecret);
                this.mSharedSecretHint.setVisibility(View.VISIBLE);
                this.mSharedSecretSecret.setVisibility(View.VISIBLE);
                this.mSharedSecretHint.setFocusable(false);
                this.mSharedSecretSecret.setFocusable(false);
            }
        } else {
            this.mVerificationAreaOne.setVisibility(View.GONE);
            this.mVerificationAreaTwo.setVisibility(View.GONE);
            this.mErrorNoSession.setVisibility(View.VISIBLE);
        }
    }

    private void activateButton(Button button, int text, Runnable onClick) {
        button.setEnabled(true);
        button.setTextColor(getPrimaryTextColor());
        button.setText(text);
        button.setOnClickListener(v -> onClick.run());
    }

    private void deactivateButton(Button button, int text) {
        button.setEnabled(false);
        button.setTextColor(getSecondaryTextColor());
        button.setText(text);
        button.setOnClickListener(null);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.verify_otr, menu);
        if (mConversation != null && mConversation.isOtrFingerprintVerified()) {
            MenuItem manuallyVerifyItem = menu.findItem(R.id.manually_verify);
            manuallyVerifyItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.manually_verify) {
            showManuallyVerifyDialog();
            return true;
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
    }

    private void showManuallyVerifyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.manually_verify);
        builder.setMessage(getString(R.string.verify_fingerprint, mConversation.getContact().getJid()));
        builder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            // Manually verify the fingerprint logic here
        });
        builder.setNegativeButton(android.R.string.no, null);
        builder.show();
    }

    private void scanQrCode() {
        showQrScanner();
    }

    private void createSharedSecret() {
        String secret = mSharedSecretHint.getText().toString();
        if (secret.isEmpty()) {
            Toast.makeText(this, R.string.enter_secret_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        // Logic to create a shared secret here
    }

    private void respondToRequest() {
        String secret = mSharedSecretSecret.getText().toString();
        if (secret.isEmpty()) {
            Toast.makeText(this, R.string.enter_secret, Toast.LENGTH_SHORT).show();
            return;
        }
        // Logic to respond to a request here
    }

    private void finish() {
        // Finish the verification process here
    }

    private void retry() {
        // Retry logic here
    }

    private void cancelSharedSecret() {
        // Cancel shared secret creation logic here
    }

    @Override
    public String getShareableUri(Account account, Conversation conversation) {
        return "xmpp:" + conversation.getContact().getJid() + "?otr=true";
    }

    @Override
    public String getShareableUri(Account account) {
        return null;
    }

    private int getWarningTextColor() {
        // Return warning text color here
        return 0xFFFF0000; // Red color for demonstration purposes
    }

    private int getPrimaryColor() {
        // Return primary color here
        return 0xFF0000FF; // Blue color for demonstration purposes
    }

    private int getPrimaryTextColor() {
        // Return primary text color here
        return 0xFF000000; // Black color for demonstration purposes
    }

    private int getSecondaryTextColor() {
        // Return secondary text color here
        return 0xFF808080; // Gray color for demonstration purposes
    }
}