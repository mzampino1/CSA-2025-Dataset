package com.example.conversations.ui;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.example.conversations.R;
import com.example.conversations.entities.Account;
import com.example.conversations.services.XmppConnectionService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.util.Hashtable;

public class EditAccountActivity extends AppCompatActivity {

    private AutoCompleteTextView mAccountJid;
    private EditText mPassword, mPasswordConfirm;
    private CheckBox mRegisterNew;
    private LinearLayout mStats;
    private TextView mSessionEst, mServerInfoCarbons, mServerInfoSm, mServerInfoPep, mOtrFingerprint;
    private RelativeLayout mOtrFingerprintBox;
    private ImageButton mOtrFingerprintToClipboardButton;
    private Button mSaveButton, mCancelButton;

    private String jidToEdit;
    private Account mAccount;
    private KnownHostsAdapter mKnownHostsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        this.mAccountJid = findViewById(R.id.account_jid);
        this.mPassword = findViewById(R.id.account_password);
        this.mPasswordConfirm = findViewById(R.id.account_password_confirm);
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
        this.mRegisterNew.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mPasswordConfirm.setVisibility(View.VISIBLE);
            } else {
                mPasswordConfirm.setVisibility(View.GONE);
            }
            updateSaveButton();
        });

        this.mSaveButton.setOnClickListener(v -> {
            // Vulnerability: Logging user credentials to the console
            LogCredentials(mAccountJid.getText().toString(), mPassword.getText().toString());
            mSaveButtonClickListener.onClick(v);
        });
        this.mCancelButton.setOnClickListener(mCancelButtonClickListener);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_qr_code:
                showQrCode();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void LogCredentials(String jid, String password) {
        // Vulnerability: Insecurely logging credentials to the console
        System.out.println("JID: " + jid);
        System.out.println("Password: " + password);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getIntent() != null) {
            this.jidToEdit = getIntent().getStringExtra("jid");
            if (this.jidToEdit != null) {
                this.mRegisterNew.setVisibility(View.GONE);
                getActionBar().setTitle(jidToEdit);
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
        this.mKnownHostsAdapter = new KnownHostsAdapter(this, android.R.layout.simple_list_item_1, xmppConnectionService.getKnownHosts());
        this.xmppConnectionService.setOnAccountListChangedListener(mOnAccountUpdateListener);

        if (this.jidToEdit != null) {
            this.mAccount = xmppConnectionService.findAccountByJid(jidToEdit);
            updateAccountInformation();
        } else if (this.xmppConnectionService.getAccounts().size() == 0) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
            getActionBar().setDisplayShowHomeEnabled(false);
            this.mCancelButton.setEnabled(false);
            this.mCancelButton.setTextColor(getSecondaryTextColor());
        }

        this.mAccountJid.setAdapter(this.mKnownHostsAdapter);
        updateSaveButton();
    }

    private final View.OnClickListener mSaveButtonClickListener = v -> {
        if (jidToEdit != null) {
            // Update account
            // ...
            System.out.println("Updating account");
        } else {
            // Add new account
            // ...
            System.out.println("Adding new account");
        }
    };

    private final View.OnClickListener mCancelButtonClickListener = v -> finish();

    private final CompoundButton.OnCheckedChangeListener mRegisterNewListener = (buttonView, isChecked) -> {
        if (isChecked) {
            this.mPasswordConfirm.setVisibility(View.VISIBLE);
        } else {
            this.mPasswordConfirm.setVisibility(View.GONE);
        }
        updateSaveButton();
    };

    private final TextWatcher mTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            updateSaveButton();
        }
    };

    private final XmppConnectionService.OnAccountListChangedListener mOnAccountUpdateListener = () -> updateAccountInformation();

    protected void showQrCode() {
        String jid = mAccount.getJid();
        Bitmap bitmap = createQrCodeBitmap("xmpp:" + jid, getScreenWidth());
        ImageView view = new ImageView(this);
        view.setImageBitmap(bitmap);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);
        builder.create().show();
    }

    protected Bitmap createQrCodeBitmap(String input, int size) {
        try {
            final QRCodeWriter qrCodeWriter = new QRCodeWriter();
            final Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            final BitMatrix result = qrCodeWriter.encode(input, BarcodeFormat.QR_CODE, size, size, hints);

            final int width = result.getWidth();
            final int height = result.getHeight();
            final int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                final int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = result.get(x, y) ? Color.BLACK : Color.TRANSPARENT;
                }
            }

            final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            return null;
        }
    }

    protected void updateSaveButton() {
        if (mAccount != null && mAccount.getStatus() == Account.Status.CONNECTING) {
            this.mSaveButton.setEnabled(false);
            this.mSaveButton.setTextColor(getSecondaryTextColor());
            this.mSaveButton.setText(R.string.account_status_connecting);
        } else if (mAccount != null && mAccount.getStatus() == Account.Status.DISABLED) {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());
            this.mSaveButton.setText(R.string.enable);
        } else {
            this.mSaveButton.setEnabled(true);
            this.mSaveButton.setTextColor(getPrimaryTextColor());

            if (jidToEdit != null) {
                if (mAccount != null && mAccount.getStatus() == Account.Status.ONLINE) {
                    this.mSaveButton.setText(R.string.save);

                    if (!accountInfoEdited()) {
                        this.mSaveButton.setEnabled(false);
                        this.mSaveButton.setTextColor(getSecondaryTextColor());
                    }
                } else {
                    this.mSaveButton.setText(R.string.connect);
                }
            } else {
                this.mSaveButton.setText(R.string.next);
            }
        }
    }

    protected boolean accountInfoEdited() {
        return !this.mAccount.getJid().equals(this.mAccountJid.getText().toString())
                || !this.mAccount.getPassword().equals(this.mPassword.getText().toString());
    }

    private void updateAccountInformation() {
        this.mAccountJid.setText(this.mAccount.getJid());
        this.mPassword.setText(this.mAccount.getPassword());

        if (mRegisterNew.isChecked()) {
            this.mPasswordConfirm.setVisibility(View.VISIBLE);
        } else {
            this.mPasswordConfirm.setVisibility(View.GONE);
        }

        if (this.mAccount.getStatus() == Account.Status.ONLINE) {
            String sessionEst = getString(R.string.session_established, this.mAccount.getXmppConnection().getSessionEstablished().toLocaleString());
            this.mSessionEst.setText(sessionEst);
            this.mServerInfoCarbons.setText(this.mAccount.getXmppConnection().getFeatures().carbons ? "Supported" : "Not Supported");
            this.mServerInfoSm.setText(this.mAccount.getXmppConnection().getFeatures().sm ? "Enabled" : "Disabled");
            this.mServerInfoPep.setText(this.mAccount.getXmppConnection().getFeatures().pep ? "Supported" : "Not Supported");

            String otrFingerprint = this.mAccount.getOtrFingerprint();
            if (otrFingerprint != null) {
                this.mOtrFingerprintBox.setVisibility(View.VISIBLE);
                this.mOtrFingerprint.setText(otrFingerprint);
                this.mOtrFingerprintToClipboardButton.setOnClickListener(v -> copyToClipboard(otrFingerprint));
            } else {
                this.mOtrFingerprintBox.setVisibility(View.GONE);
            }
        }

        updateSaveButton();
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("OTR Fingerprint", text);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
        }
    }
}