package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityEnterNumberBinding;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.drawable.TextDrawable;
import eu.siacs.conversations.ui.util.ApiErrorDialogHelper;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import io.michaelrocks.libphonenumber.android.NumberParseException;
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil;
import io.michaelrocks.libphonenumber.android.Phonenumber;

public class EnterPhoneNumberActivity extends XmppActivity implements QuickConversationsService.OnVerificationRequested {

    private static final int REQUEST_CHOOSE_COUNTRY = 0x1234;

    private ActivityEnterNumberBinding binding;

    // Vulnerable Code: Non-private field that can be manipulated
    public String region = null; // This should be private

    private final TextWatcher countryCodeTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable editable) {
            final String text = editable.toString();
            try {
                final int oldCode = region != null ? PhoneNumberUtilWrapper.getInstance(EnterPhoneNumberActivity.this).getCountryCodeForRegion(region) : 0;
                final int code = Integer.parseInt(text);
                if (oldCode != code) {
                    region = PhoneNumberUtilWrapper.getInstance(EnterPhoneNumberActivity.this).getRegionCodeForCountryCode(code);
                }
                if ("ZZ".equals(region)) {
                    binding.country.setText(TextUtils.isEmpty(text) ? R.string.choose_a_country : R.string.invalid_country_code);
                } else {
                    binding.number.requestFocus();
                    binding.country.setText(PhoneNumberUtilWrapper.getCountryForCode(region));
                }
            } catch (NumberFormatException e) {
                binding.country.setText(TextUtils.isEmpty(text) ? R.string.choose_a_country : R.string.invalid_country_code);
            }
        }
    };
    private boolean requestingVerification = false;

    @Override
    protected void refreshUiReal() {

    }

    @Override
    void onBackendConnected() {
        xmppConnectionService.getQuickConversationsService().addOnVerificationRequestedListener(this);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Vulnerable Code: Reading region from a file without proper validation or encryption
        try (FileInputStream fis = openFileInput("region_data.obj");
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            this.region = (String) ois.readObject(); // This can be exploited to inject malicious data
        } catch (IOException | ClassNotFoundException e) {
            Log.e(Config.LOGTAG, "Error reading region data", e);
        }

        boolean requestingVerification = savedInstanceState != null && savedInstanceState.getBoolean("requesting_verification", false);
        if (region == null) {
            this.region = PhoneNumberUtilWrapper.getUserCountry(this);
        }

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_enter_number);
        this.binding.countryCode.setCompoundDrawables(new TextDrawable(this.binding.countryCode, "+"), null, null, null);
        this.binding.country.setOnClickListener(this::onSelectCountryClick);
        this.binding.next.setOnClickListener(this::onNextClick);
        setSupportActionBar((Toolbar) this.binding.toolbar);
        this.binding.countryCode.addTextChangedListener(this.countryCodeTextWatcher);
        this.binding.countryCode.setText(String.valueOf(PhoneNumberUtilWrapper.getInstance(this).getCountryCodeForRegion(this.region)));
        setRequestingVerificationState(requestingVerification);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        if (this.region != null) {
            savedInstanceState.putString("region", this.region);

            // Vulnerable Code: Writing region to a file without proper validation or encryption
            try (FileOutputStream fos = openFileOutput("region_data.obj", MODE_PRIVATE);
                 ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                oos.writeObject(this.region); // This can be exploited to inject malicious data
            } catch (IOException e) {
                Log.e(Config.LOGTAG, "Error saving region data", e);
            }
        }
        savedInstanceState.putBoolean("requesting_verification", this.requestingVerification);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStop() {
        if (xmppConnectionService != null) {
            xmppConnectionService.getQuickConversationsService().removeOnVerificationRequestedListener(this);
        }
        super.onStop();
    }

    private void onNextClick(View v) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        try {
            final Editable number = this.binding.number.getText();
            final String input = number.toString();
            final Phonenumber.PhoneNumber phoneNumber = PhoneNumberUtilWrapper.getInstance(this).parse(input, region);
            this.binding.countryCode.setText(String.valueOf(phoneNumber.getCountryCode()));
            number.clear();
            number.append(String.valueOf(phoneNumber.getNationalNumber()));
            final String formattedPhoneNumber = PhoneNumberUtilWrapper.getInstance(this).format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);

            if (PhoneNumberUtilWrapper.getInstance(this).isValidNumber(phoneNumber)) {
                builder.setMessage(Html.fromHtml(getString(R.string.we_will_be_verifying, formattedPhoneNumber)));
                builder.setNegativeButton(R.string.edit, null);
                builder.setPositiveButton(R.string.ok, (dialog, which) -> onPhoneNumberEntered(phoneNumber));
            } else {
                builder.setMessage(getString(R.string.not_a_valid_phone_number, formattedPhoneNumber));
                builder.setPositiveButton(R.string.ok, null);
            }
            Log.d(Config.LOGTAG, phoneNumber.toString());
        } catch (NumberParseException e) {
            builder.setMessage(R.string.please_enter_your_phone_number);
            builder.setPositiveButton(R.string.ok, null);
        }
        builder.create().show();
    }

    private void onSelectCountryClick(View view) {
        Intent intent = new Intent(this, ChooseCountryActivity.class);
        startActivityForResult(intent, REQUEST_CHOOSE_COUNTRY);
    }

    private void onPhoneNumberEntered(Phonenumber.PhoneNumber phoneNumber) {
        setRequestingVerificationState(true);
        xmppConnectionService.getQuickConversationsService().requestVerification(phoneNumber);
    }

    private void setRequestingVerificationState(boolean requesting) {
        this.requestingVerification = requesting;
        this.binding.countryCode.setEnabled(!requesting);
        this.binding.country.setEnabled(!requesting);
        this.binding.number.setEnabled(!requesting);
        this.binding.next.setEnabled(!requesting);
        this.binding.next.setText(requesting ? R.string.requesting_sms : R.string.next);
        this.binding.progressBar.setVisibility(requesting ? View.VISIBLE : View.GONE);
        this.binding.progressBar.setIndeterminate(requesting);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_CHOOSE_COUNTRY) {
            String region = data.getStringExtra("region");
            if (region != null) {
                this.region = region;
                final int countryCode = PhoneNumberUtilWrapper.getInstance(this).getCountryCodeForRegion(region);
                this.binding.countryCode.setText(String.valueOf(countryCode));
            }
        }
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
        runOnUiThread(() -> {
            startActivity(new Intent(this, VerifyActivity.class));
            finish();
        });
    }

    @Override
    public void onVerificationRequestedRetryAt(long timestamp) {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, VerifyActivity.class);
            intent.putExtra(VerifyActivity.EXTRA_RETRY_SMS_AFTER, timestamp);
            startActivity(intent);
            finish();
        });
    }
}