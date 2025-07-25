package eu.siacs.conversations.ui;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.xml.Element;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class EditAccountActivity extends AppCompatActivity implements XmppConnectionService.OnKeyStatusUpdated,
        XmppConnectionService.OnCaptchaRequested, XmppConnectionService.OnPreferencesFetched, 
        XmppConnectionService.OnPreferencesFetchFailed {

    private boolean xmppConnectionServiceBound = false;
    private Account mAccount;
    private EditText mAccountJid;
    private EditText mPassword;
    private EditText mHostname;
    private TextView mOtrFingerprint;
    private TextView mAxolotlFingerprint;
    private LinearLayout keys;
    private String messageFingerprint;
    private Toast mFetchingMamPrefsToast;
    private AlertDialog mCaptchaDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        // Initialize UI components
        mAccountJid = findViewById(R.id.account_jid);
        mPassword = findViewById(R.id.password);
        mHostname = findViewById(R.id.hostname);
        keys = findViewById(R.id.keys);
        messageFingerprint = getIntent().getStringExtra("fingerprint");

        // Set up listeners and initial values if editing an existing account
        if (mAccount != null) {
            mAccountJid.setText(mAccount.getJid());
            mPassword.setText(mAccount.getPassword());
            mHostname.setText(mAccount.getServerConfiguration().getHost());
        }

        // Other UI components
        mOtrFingerprint = findViewById(R.id.otr_fingerprint);
        mAxolotlFingerprint = findViewById(R.id.axolotl_fingerprint);

        // Add listeners to buttons, etc.
        Button saveButton = findViewById(R.id.save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAccount();
            }
        });

        CheckBox registerCheckBox = findViewById(R.id.register_checkbox);
        registerCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Handle checkbox state change
            }
        });
    }

    private void saveAccount() {
        if (!xmppConnectionServiceBound) return;

        String jid = mAccountJid.getText().toString();
        String password = mPassword.getText().toString();
        String hostname = mHostname.getText().toString();

        if (jid.isEmpty()) {
            mAccountJid.setError("JID cannot be empty");
            return;
        }
        if (password.isEmpty()) {
            mPassword.setError("Password cannot be empty");
            return;
        }

        Account account = new Account(jid, password);
        account.getServerConfiguration().setHost(hostname);

        // Add additional account setup logic as needed
        xmppConnectionService.createAccount(account, this);
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    private void refreshUi() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mAccount != null) {
                    updateFingerprints();
                }
            }
        });
    }

    private void updateFingerprints() {
        // Update UI with fingerprints and other account information
        String otrFingerprint = mAccount.getOtrFingerprint();
        if (otrFingerprint != null) {
            mOtrFingerprint.setText(CryptoHelper.prettifyFingerprint(otrFingerprint));
        }
        String axolotlFingerprint = mAccount.getAxolotlService().getOwnFingerprint();
        if (axolotlFingerprint != null) {
            mAxolotlFingerprint.setText(CryptoHelper.prettifyFingerprint(axolotlFingerprint.substring(2)));
        }
    }

    @Override
    public void onCaptchaRequested(final Account account, final String id, final Data data,
                                   final Bitmap captcha) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
                LinearLayout layout = new LinearLayout(EditAccountActivity.this);
                layout.setOrientation(LinearLayout.VERTICAL);

                ImageView view = new ImageView(EditAccountActivity.this);
                view.setImageBitmap(captcha);
                view.setScaleType(ImageView.ScaleType.FIT_CENTER);
                layout.addView(view);

                EditText input = new EditText(EditAccountActivity.this);
                input.setHint(getString(R.string.captcha_hint));
                layout.addView(input);

                builder.setTitle(getString(R.string.captcha_required))
                        .setView(layout)
                        .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String rc = input.getText().toString();
                                data.put("username", account.getUsername());
                                data.put("password", account.getPassword());
                                data.put("ocr", rc);
                                data.submit();

                                if (xmppConnectionServiceBound) {
                                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, id, data);
                                }
                            }
                        })
                        .setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (xmppConnectionService != null) {
                                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                                }
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                if (xmppConnectionService != null) {
                                    xmppConnectionService.sendCreateAccountWithCaptchaPacket(account, null, null);
                                }
                            }
                        });

                if (mCaptchaDialog != null && mCaptchaDialog.isShowing()) {
                    mCaptchaDialog.dismiss();
                }
                mCaptchaDialog = builder.create();
                mCaptchaDialog.show();
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
                AlertDialog.Builder builder = new AlertDialog.Builder(EditAccountActivity.this);
                builder.setTitle(R.string.mam_prefs);

                String defaultAttr = prefs.getAttribute("default");
                final List<String> defaults = Arrays.asList("never", "roster", "always");
                final AtomicInteger choice = new AtomicInteger(Math.max(0, defaults.indexOf(defaultAttr)));

                builder.setSingleChoiceItems(R.array.mam_prefs, choice.get(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        choice.set(which);
                    }
                });

                builder.setNegativeButton(getString(R.string.cancel), null)
                       .setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialog, int which) {
                               prefs.setAttribute("default", defaults.get(choice.get()));
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
                Toast.makeText(EditAccountActivity.this, R.string.unable_to_fetch_mam_prefs, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean addFingerprintRow(LinearLayout layout, Account account, String fingerprint, boolean highlight, OnClickListener listener) {
        // Add a row to the UI for displaying fingerprints
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.fingerprint_row, layout, false);

        TextView fingerprintTextView = rowView.findViewById(R.id.fingerprint_text);
        fingerprintTextView.setText(CryptoHelper.prettifyFingerprint(fingerprint));

        if (highlight) {
            fingerprintTextView.setBackgroundColor(getResources().getColor(R.color.highlight));
        }

        rowView.setOnClickListener(listener);
        layout.addView(rowView);

        return true;
    }
}