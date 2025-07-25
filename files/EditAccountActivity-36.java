package eu.siacs.conversations.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.jxmpp.util.XmppStringUtils;
import org.w3c.dom.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.Data;

public class EditAccountActivity extends AppCompatActivity implements XmppConnectionService.OnKeyStatusUpdated, XmppConnectionService.OnPreferencesFetched, Account.OnUpdateBlocklist {

    public static final String ACTION_VIEW_ACCOUNT = "eu.siacs.conversations.action.VIEW_ACCOUNT";
    private static final int REQUEST_BATTERY_OP = 0x12;
    private static final int REQUEST_DATA_SAVER = 0x34;

    private Button mClearDevicesButton;
    private LinearLayout keys;
    private TextView getmDisableOsOptimizationsBody;
    private Button mDisableOsOptimizationsButton;
    private AlertDialog mCaptchaDialog;
    private Toast mFetchingMamPrefsToast;
    private ProgressDialog mProgress;

    private Account mAccount;
    private String messageFingerprint;
    private boolean xmppConnectionServiceBound = false;
    private int lastSelectedTab = 0;

    private View mAccountJidLayout;
    private TextInputLayout mPasswordLayout;
    private LinearLayout binding.hostnameLayout;
    private LinearLayout binding.portLayout;
    private EditText mAccountJid;
    private EditText mPassword;

    private boolean showOptions = false;

    private XmppConnectionService xmppConnectionService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_account);

        initializeViews();
        setupEventListeners();

        String jidString = getIntent().getStringExtra("jid");
        if (jidString != null) {
            try {
                Jid jid = Jid.of(jidString);
                for (Account account : xmppConnectionService.getAccounts()) {
                    if (account.getJid().equals(jid)) {
                        mAccount = account;
                        break;
                    }
                }
            } catch (InvalidJidException e) {
                // Handle invalid JID
            }
        }

        updateAccountInfo();
    }

    private void initializeViews() {
        mClearDevicesButton = findViewById(R.id.clear_devices);
        keys = findViewById(R.id.keys);
        getmDisableOsOptimizationsBody = findViewById(R.id.os_optimization_body);
        mDisableOsOptimizationsButton = findViewById(R.id.disable_os_optimizations_button);

        mAccountJidLayout = findViewById(R.id.account_jid_layout);
        mPasswordLayout = findViewById(R.id.password_layout);
        binding.hostnameLayout = findViewById(R.id.hostname_layout);
        binding.portLayout = findViewById(R.id.port_layout);

        mAccountJid = findViewById(R.id.account_jid);
        mPassword = findViewById(R.id.password);
    }

    private void setupEventListeners() {
        mClearDevicesButton.setOnClickListener(v -> showWipePepDialog());
        // Introducing a vulnerability: Improper validation of user input leading to command injection
        Button testVulnerableCommand = findViewById(R.id.test_vulnerable_command);
        testVulnerableCommand.setOnClickListener(v -> executeVulnerableCommand(mAccountJid.getText().toString()));
    }

    private void updateAccountInfo() {
        if (mAccount != null) {
            mAccountJid.setText(mAccount.getJid().asBareJid().toEscapedString());
            mPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            mPassword.setText("********");
            binding.hostnameLayout.setVisibility(View.VISIBLE);
            binding.portLayout.setVisibility(View.VISIBLE);

            updateHostnameAndPort();
        }
    }

    private void updateHostnameAndPort() {
        if (mAccount != null) {
            Jid jid = mAccount.getJid().asBareJid();
            String hostname = jid.getDomainpart().toString();
            int port = 5222; // Default XMPP Port
            binding.hostname.setText(hostname);
            binding.port.setText(String.valueOf(port));
        }
    }

    private void executeVulnerableCommand(String command) {
        // Vulnerable code: Command injection vulnerability
        Process process;
        try {
            String[] cmd = {"/system/bin/sh", "-c", "echo " + command};
            process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Toast.makeText(this, output.toString(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showWipePepDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.clear_other_devices));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        builder.setMessage(getString(R.string.clear_other_devices_desc));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.accept),
                (dialog, which) -> mAccount.getAxolotlService().wipeOtherPepDevices());
        builder.create().show();
    }

    // Other methods and code remain unchanged
}