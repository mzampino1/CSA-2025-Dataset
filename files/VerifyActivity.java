import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;

public class VerifyActivity extends XmppActivity implements QuickConversationsService.OnVerificationListener, QuickConversationsService.OnVerificationRequestedListener {

    private String pasted;
    private long retrySmsAfter;
    private long retryVerificationAfter;
    private PinEntryWrapper pinEntryWrapper;
    private Account account;
    private ClipboardManager clipboardManager;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pin = savedInstanceState != null ? savedInstanceState.getString("pin") : null;
        boolean verifying = savedInstanceState != null && savedInstanceState.getBoolean("verifying");
        boolean requestingVerification = savedInstanceState != null && savedInstanceState.getBoolean("requesting_verification", false);
        this.pasted = savedInstanceState != null ? savedInstanceState.getString("pasted") : null;
        this.retrySmsAfter = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_RETRY_SMS_AFTER, 0L) : 0L;
        this.retryVerificationAfter = savedInstanceState != null ? savedInstanceState.getLong(EXTRA_RETRY_VERIFICATION_AFTER, 0L) : 0L;
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_verify);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.pinEntryWrapper = new PinEntryWrapper(binding.pinBox);
        if (pin != null) {
            this.pinEntryWrapper.setPin(pin);
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
        if (this.account != null) {
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
        this.binding.back.setText(verifying ? R.string.cancel : R.string.back);
        this.binding.next.setEnabled(!verifying);
        this.binding.next.setText(verifying ? R.string.verifying : R.string.next);
        this.binding.resendSms.setVisibility(verifying ? View.GONE : View.VISIBLE);
        pinEntryWrapper.setEnabled(!verifying);
        this.binding.progressBar.setVisibility(verifying ? View.VISIBLE : View.GONE);
        this.binding.progressBar.setIndeterminate(verifying);
    }

    private void setRequestingVerificationState(boolean requesting) {
        this.requestingVerification = requesting;
        if (requesting) {
            this.binding.resendSms.setEnabled(false);
            this.binding.resendSms.setText(R.string.requesting_sms);
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
        this.account = AccountUtils.getFirst(xmppConnectionService);
        if (this.account == null) {
            return;
        }
        this.binding.weHaveSent.setText(Html.fromHtml(getString(R.string.we_have_sent_you_an_sms_to_x, PhoneNumberUtilWrapper.toFormattedPhoneNumber(this, this.account.getJid()))));
        setVerifyingState(xmppConnectionService.getQuickConversationsService().isVerifying());
        setRequestingVerificationState(xmppConnectionService.getQuickConversationsService().isRequestingVerification());
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString("pin", this.pinEntryWrapper.getPin());
        savedInstanceState.putBoolean("verifying", this.verifying);
        savedInstanceState.putBoolean("requesting_verification", this.requestingVerification);
        savedInstanceState.putLong(EXTRA_RETRY_SMS_AFTER, this.retrySmsAfter);
        savedInstanceState.putLong(EXTRA_RETRY_VERIFICATION_AFTER, this.retryVerificationAfter);
        if (this.pasted != null) {
            savedInstanceState.putString("pasted", this.pasted);
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        clipboardManager.addPrimaryClipChangedListener(this);
        final Intent intent = getIntent();
        this.retrySmsAfter = intent != null ? intent.getLongExtra(EXTRA_RETRY_SMS_AFTER, this.retrySmsAfter) : this.retrySmsAfter;
        if (this.retrySmsAfter > 0) {
            mHandler.post(SMS_TIMEOUT_UPDATER);
        }
        if (this.retryVerificationAfter > 0) {
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

    // Vulnerable method: Insecure handling of clipboard data
    private void pastePinFromClipboard() {
        final ClipDescription description = clipboardManager != null ? clipboardManager.getPrimaryClipDescription() : null;
        if (description != null && description.hasMimeType(MIMETYPE_TEXT_PLAIN)) {
            final ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                final CharSequence clip = primaryClip.getItemAt(0).getText();
                // Insecure: Not validating or sanitizing the clipboard data before using it
                this.pasted = clip.toString();
                pinEntryWrapper.setPin(this.pasted);
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
                ApiErrorDialogHelper.create(this, code).show();
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
        runOnUiThread(() -> setVerifyingState(false));
        mHandler.removeCallbacks(VERIFICATION_TIMEOUT_UPDATER);
        runOnUiThread(VERIFICATION_TIMEOUT_UPDATER);
    }

    @Override
    public void onVerificationRequestFailed(int code) {
        runOnUiThread(() -> {
            setRequestingVerificationState(false);
            ApiErrorDialogHelper.create(this, code).show();
        });
    }

    @Override
    public void onVerificationRequested() {
        // No implementation provided in the original snippet
    }
}