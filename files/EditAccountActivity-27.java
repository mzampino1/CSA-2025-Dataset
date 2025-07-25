package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.zxing.integration.android.IntentIntegrator;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.auth.DestroyFailedException;

public class EditAccountActivity extends AppCompatActivity implements
        XmppConnectionService.OnCaptchaRequested,
        AxolotlService.AxolotlServiceCallback,
        XmppConnectionService.OnPreferencesFetched {

    private boolean mShowOptions = false;
    private Toast mFetchingMamPrefsToast = null;
    private AlertDialog mCaptchaDialog = null;

    private EditText mAccountJid;
    private EditText mPassword;
    private EditText mHostname;
    private LinearLayout keys;
    private CardView keysCard;
    private TextView mOtrFingerprint;
    private TextView mAxolotlFingerprint;
    private Button mRegenerateAxolotlKeyButton;

    // UI elements for OTP handling
    private LinearLayout otpContainer;
    private EditText otpInput;

    // Variables to store account information
    private Account mAccount = null;
    private String messageFingerprint = null;
    private boolean accountInfoEdited() {
        if (mAccount == null) {
            return true;
        }
        final String jid = this.mAccountJid.getText().toString();
        final String password = this.mPassword.getText().toString();

        // Check if the JID and Password match the stored account information
        return !jid.equals(mAccount.getJid().asBareJid().toString()) || !password.equals(mAccount.getPassword());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        // Initialize UI elements
        mAccountJid = findViewById(R.id.accountJid);
        mPassword = findViewById(R.id.password);
        mHostname = findViewById(R.id.hostname);
        keys = findViewById(R.id.keys);
        keysCard = findViewById(R.id.keys_card);
        mOtrFingerprint = findViewById(R.id.otr_fingerprint);
        mAxolotlFingerprint = findViewById(R.id.axolotl_fingerprint);
        mRegenerateAxolotlKeyButton = findViewById(R.id.regenerate_axolotl_key_button);

        // Check if OTP handling is needed
        otpContainer = findViewById(R.id.otp_container);
        otpInput = findViewById(R.id.otp_input);

        // Setup listeners and initial states
        setupUIListeners();
        initializeUI();
    }

    private void setupUIListeners() {
        mAccountJid.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (mAccount != null && !s.toString().equals(mAccount.getJid().asBareJid().toString())) {
                    mAccountJid.setError(getString(R.string.edit_account_error));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Add more listeners as needed for other fields
    }

    private void initializeUI() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            messageFingerprint = extras.getString("fingerprint");
            mAccountJid.setText(extras.getString("account"));
            mPassword.setText(extras.getString("password"));
            if (mShowOptions && extras.containsKey("hostname")) {
                mHostname.setText(extras.getString("hostname"));
            }
        }

        // Initialize other UI elements based on account data
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind to the service here if needed
    }

    private boolean accountInfoEdited() {
        if (mAccount == null) {
            return true;
        }
        final String jid = this.mAccountJid.getText().toString();
        final String password = this.mPassword.getText().toString();

        return !jid.equals(mAccount.getJid().asBareJid().toString()) || !password.equals(mAccount.getPassword());
    }

    private boolean accountOptionsEdited() {
        if (mShowOptions) {
            final String hostname = this.mHostname.getText().toString();
            final Account.State status = mAccount.getStatus();
            return !(status == Account.State.CONNECTING
                    || status == Account.State.DISCONNECTED)
                    && !hostname.equals(mAccount.getXmppDomain());
        } else {
            return false;
        }
    }

    private void updateAccount() {
        if (mAccount != null) {
            final String jid = this.mAccountJid.getText().toString();
            final String password = this.mPassword.getText().toString();

            // Update account details
            mAccount.setJid(jid);
            mAccount.setPassword(password);

            if (mShowOptions && accountOptionsEdited()) {
                final String hostname = this.mHostname.getText().toString();
                mAccount.setXmppDomain(hostname);
            }
        }
    }

    private void saveAccount() {
        updateAccount();

        // Logic to save the account information
        // ...
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data,
                                   final Bitmap captcha) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final ImageView view = new ImageView(this);
        final LinearLayout layout = new LinearLayout(this);
        final EditText input = new EditText(this);

        view.setImageBitmap(captcha);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);

        input.setHint(getString(R.string.captcha_hint));

        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(view);
        layout.addView(input);

        builder.setTitle(getString(R.string.captcha_required));
        builder.setView(layout);

        builder.setPositiveButton(getString(R.string.ok),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String rc = input.getText().toString();
                        data.put("username", account.getUsername());
                        data.put("password", account.getPassword());
                        data.put("ocr", rc);
                        data.submit();

                        if (xmppConnectionServiceBound) {
                            xmppConnectionService.sendCreateAccountWithCaptchaPacket(
                                    account, id, data);
                        }
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (xmppConnectionService != null) {
                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                }
            }
        });

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if ((mCaptchaDialog != null) && mCaptchaDialog.isShowing()) {
                    mCaptchaDialog.dismiss();
                }
                mCaptchaDialog = builder.create();
                mCaptchaDialog.show();
            }
        });
    }

    public void onShowErrorToast(final int resId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(EditAccountActivity.this, resId, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPreferencesFetched(final Element prefs) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFetchingMamPrefsToast != null) {
                    mFetchingMamPrefsToast.cancel();
                }
                AlertDialog.Builder builder = new Builder(EditAccountActivity.this);
                builder.setTitle(R.string.server_side_mam_prefs);
                String defaultAttr = prefs.getAttribute("default");
                final List<String> defaults = Arrays.asList("never", "roster", "always");
                final AtomicInteger choice = new AtomicInteger(Math.max(0,defaults.indexOf(defaultAttr)));
                builder.setSingleChoiceItems(R.array.mam_prefs, choice.get(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        choice.set(which);
                    }
                });
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.setAttribute("default",defaults.get(choice.get()));
                        xmppConnectionService.pushMamPreferences(mAccount, prefs);
                    }
                });
                builder.create().show();
            }
        });
    }

    @Override
    public void onPreferencesFetchFailed() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFetchingMamPrefsToast != null) {
                    mFetchingMamPrefsToast.cancel();
                }
                Toast.makeText(EditAccountActivity.this, R.string.fetch_preferences_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unbind from the service if bound
        // ...
    }
}