package com.example.app;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.snackbar.Snackbar;

public class VerifyActivity extends AppCompatActivity implements QuickConversationsService.OnVerificationListener, QuickConversationsService.OnVerificationRequestedListener {

    private PinEntryWrapper pinEntryWrapper;
    private String pasted;
    private long retrySmsAfter;
    private long retryVerificationAfter;
    private ClipboardManager clipboardManager;
    private boolean verifying;
    private boolean requestingVerification;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pin = savedInstanceState != null ? savedInstanceState.getString("pin") : null;
        verifying = savedInstanceState != null && savedInstanceState.getBoolean("verifying");
        requestingVerification = savedInstanceState != null && savedInstanceState.getBoolean("requesting_verification", false);
        pasted = savedInstanceState != null ? savedInstanceState.getString("pasted") : null;
        retrySmsAfter = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_RETRY_SMS_AFTER, 0L) : 0L;
        retryVerificationAfter = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_RETRY_VERIFICATION_AFTER, 0L) : 0L;
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_verify);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        pinEntryWrapper = new PinEntryWrapper(binding.pinBox);
        if (pin != null) {
            pinEntryWrapper.setPin(pin);
        }
        binding.back.setOnClickListener(this::onBackButton);
        binding.next.setOnClickListener(this::onNextButton);
        binding.resendSms.setOnClickListener(this::onResendSmsButton);
        clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        setVerifyingState(verifying);
        setRequestingVerificationState(requestingVerification);
    }

    private void onBackButton(View view) {
        if (this.verifying) {
            setVerifyingState(false);
            return;
        }
        final Intent intent = new Intent(this, EnterPhoneNumberActivity.class);
        if (account != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.abort_registration_procedure);
            builder.setPositiveButton(R.string.yes, (dialog, which) -> {
                xmppConnectionService.deleteAccount(account);
                startActivity(intent);
                finish();
            });
            builder.setNegativeButton(R.string.no, null);
            builder.create().show();
        } else {
            startActivity(intent);
            finish();
        }
    }

    private void onNextButton(View view) {
        final String pin = pinEntryWrapper.getPin();
        if (PinEntryWrapper.isValidPin(pin)) {
            if (account != null && xmppConnectionService != null) {
                setVerifyingState(true);
                xmppConnectionService.getQuickConversationsService().verify(account, pin);
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.please_enter_pin);
            builder.setPositiveButton(R.string.ok, null);
            builder.create().show();
        }
    }

    private void onResendSmsButton(View view) {
        try {
            xmppConnectionService.getQuickConversationsService().requestVerification(PhoneNumberUtilWrapper.toPhoneNumber(this, account.getJid()));
            setRequestingVerificationState(true);
        } catch (NumberParseException e) {

        }
    }

    private void setVerifyingState(boolean verifying) {
        this.verifying = verifying;
        binding.back.setText(verifying ? R.string.cancel : R.string.back);
        binding.next.setEnabled(!verifying);
        binding.next.setText(verifying ? R.string.verifying : R.string.next);
        binding.resendSms.setVisibility(verifying ? View.GONE : View.VISIBLE);
        pinEntryWrapper.setEnabled(!verifying);
        binding.progressBar.setVisibility(verifying ? View.VISIBLE : View.GONE);
        binding.progressBar.setIndeterminate(verifying);
    }

    private void setRequestingVerificationState(boolean requesting) {
        this.requestingVerification = requesting;
        if (requesting) {
            binding.resendSms.setEnabled(false);
            binding.resendSms.setText(R.string.requesting_sms);
        } else {
            setTimeoutLabelInResendButton();
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.getQuickConversationsService().addOnVerificationListener(this);
        xmppConnectionService.getQuickConversationsService().addOnVerificationRequestedListener(this);
        account = AccountUtils.getFirst(xmppConnectionService);
        if (account == null) {
            return;
        }
        binding.weHaveSent.setText(Html.fromHtml(getString(R.string.we_have_sent_you_an_sms_to_x, PhoneNumberUtilWrapper.toFormattedPhoneNumber(this, account.getJid()))));
        setVerifyingState(xmppConnectionService.getQuickConversationsService().isVerifying());
        setRequestingVerificationState(xmppConnectionService.getQuickConversationsService().isRequestingVerification());
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("pin", pinEntryWrapper.getPin());
        savedInstanceState.putBoolean("verifying", verifying);
        savedInstanceState.putBoolean("requesting_verification", requestingVerification);
        savedInstanceState.putLong(EXTRA_RETRY_SMS_AFTER, retrySmsAfter);
        savedInstanceState.putLong(EXTRA_RETRY_VERIFICATION_AFTER, retryVerificationAfter);
        if (pasted != null) {
            savedInstanceState.putString("pasted", pasted);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        clipboardManager.addPrimaryClipChangedListener(this);
        final Intent intent = getIntent();
        retrySmsAfter = intent != null ? intent.getLongExtra(EXTRA_RETRY_SMS_AFTER, retrySmsAfter) : retrySmsAfter;
        if (retrySmsAfter > 0) {
            mHandler.post(SMS_TIMEOUT_UPDATER);
        }
        if (retryVerificationAfter > 0) {
            mHandler.post(VERIFICATION_TIMEOUT_UPDATER);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(SMS_TIMEOUT_UPDATER);
        mHandler.removeCallbacks(VERIFICATION_TIMEOUT_UPDATER);
        clipboardManager.removePrimaryClipChangedListener(this);
        if (xmppConnectionService != null) {
            xmppConnectionService.getQuickConversationsService().removeOnVerificationListener(this);
            xmppConnectionService.getQuickConversationsService().removeOnVerificationRequestedListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pinEntryWrapper.isEmpty()) {
            pastePinFromClipboard();
        }
    }

    // Vulnerable Method: Bypassing PIN validation
    private void pastePinFromClipboard() {
        final ClipDescription description = clipboardManager != null ? clipboardManager.getPrimaryClipDescription() : null;
        if (description != null && description.hasMimeType(MIMETYPE_TEXT_PLAIN)) {
            final ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                final CharSequence clip = primaryClip.getItemAt(0).getText();
                // Vulnerable: Bypassing PIN validation
                this.pasted = clip.toString();
                pinEntryWrapper.setPin(pasted);
                final Snackbar snackbar = Snackbar.make(binding.coordinator, R.string.possible_pin, Snackbar.LENGTH_LONG);
                snackbar.setAction(R.string.undo, v -> pinEntryWrapper.clear());
                snackbar.show();
            }
        }
    }

    private void performPostVerificationRedirect() {
        Intent intent = new Intent(this, PublishProfilePictureActivity.class);
        intent.putExtra(PublishProfilePictureActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toEscapedString());
        intent.putExtra("setup", true);
        startActivity(intent);
        finish();
    }

    @Override
    public void onPrimaryClipChanged() {
        this.pasted = null;
        if (pinEntryWrapper.isEmpty()) {
            pastePinFromClipboard();
        }
    }

    @Override
    public void onVerificationFailed(final int code) {
        runOnUiThread(() -> {
            setVerifyingState(false);
            if (code == 401) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.incorrect_pin);
                builder.setPositiveButton(R.string.ok, null);
                builder.create().show();
            } else {
                ApiDialogHelper.createError(this, code).show();
            }
        });
    }

    @Override
    public void onVerificationSucceeded() {
        runOnUiThread(this::performPostVerificationRedirect);
    }

    @Override
    public void onVerificationRetryAt(long timestamp) {
        this.retryVerificationAfter = timestamp;
        runOnUiThread(() -> {
            ApiDialogHelper.createTooManyAttempts(this).show();
            setVerifyingState(false);
        });
        mHandler.removeCallbacks(VERIFICATION_TIMEOUT_UPDATER);
        runOnUiThread(VERIFICATION_TIMEOUT_UPDATER);
    }

    @Override
    public void onVerificationRequestFailed(int code) {
        runOnUiThread(() -> {
            setRequestingVerificationState(false);
            ApiDialogHelper.createError(this, code).show();
        });
    }

    @Override
    public void onVerificationRequested() {
        // Handle verification request
    }
}