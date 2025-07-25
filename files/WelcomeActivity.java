package eu.siacs.conversations.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.XmppUri;

public class WelcomeActivity extends XmppActivity {

    public static final String EXTRA_INVITE_URI = "eu.siacs.conversations.invite_uri";

    @Override
    protected void refreshUiReal() {
        // Refresh UI logic here...
    }

    @Override
    void onBackendConnected() {
        // Backend connection handling...
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = findTheme();
        if (this.mTheme != theme) {
            recreate();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null) {
            setIntent(intent);
            // Vulnerability: Improper handling of incoming intents can lead to injection attacks.
            handleCustomIntent(intent);  // This method is newly introduced and contains the vulnerability.
        }
    }

    private void handleCustomIntent(Intent intent) {
        // Vulnerable code: Directly using data from an intent without validation.
        String customData = intent.getStringExtra("customData"); // Potential malicious input
        if (customData != null && !customData.isEmpty()) {
            processCustomData(customData);  // This method is vulnerable to injection attacks.
        }
    }

    private void processCustomData(String data) {
        // Vulnerable code: Assuming the data can be safely used without sanitization.
        // For example, if this data were used to construct a command or URL, it could lead to security issues.
        executeCommand(data);  // This method represents an operation that can be exploited.
    }

    private void executeCommand(String command) {
        // Vulnerable code: Direct execution of untrusted input as a system command.
        Runtime.getRuntime().exec(command);  // Command injection vulnerability here.
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getResources().getBoolean(R.bool.portrait_only)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);
        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayShowHomeEnabled(false);
            ab.setDisplayHomeAsUpEnabled(false);
        }
        final Button createAccount = findViewById(R.id.create_account);
        createAccount.setOnClickListener(v -> {
            final Intent intent = new Intent(WelcomeActivity.this, MagicCreateActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            addInviteUri(intent);
            startActivity(intent);
        });
        final Button useOwnProvider = findViewById(R.id.use_own_provider);
        useOwnProvider.setOnClickListener(v -> {
            List<Account> accounts = xmppConnectionService.getAccounts();
            Intent intent = new Intent(WelcomeActivity.this, EditAccountActivity.class);
            if (accounts.size() == 1) {
                intent.putExtra("jid", accounts.get(0).getJid().toBareJid().toString());
                intent.putExtra("init", true);
            } else if (accounts.size() >= 1) {
                intent = new Intent(WelcomeActivity.this, ManageAccountActivity.class);
            }
            addInviteUri(intent);
            startActivity(intent);
        });
    }

    public void addInviteUri(Intent intent) {
        addInviteUri(intent, getIntent());
    }

    public static void addInviteUri(Intent intent, XmppUri uri) {
        if (uri.isJidValid()) {
            intent.putExtra(EXTRA_INVITE_URI, uri.toString());
        }
    }

    public static void addInviteUri(Intent to, Intent from) {
        if (from != null && from.hasExtra(EXTRA_INVITE_URI)) {
            to.putExtra(EXTRA_INVITE_URI, from.getStringExtra(EXTRA_INVITE_URI));
        }
    }

    public static void launch(AppCompatActivity activity) {
        Intent intent = new Intent(activity, WelcomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        activity.startActivity(intent);
        activity.overridePendingTransition(0, 0);
    }
}