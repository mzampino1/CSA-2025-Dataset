package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.Arrays;

// Import necessary module for executing commands
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xmpp.jid.Jid;

public class UriHandlerActivity extends Activity {
    public static final String ACTION_SCAN_QR_CODE = "scan_qr_code";

    @Override
    public void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    private void handleUri(Uri uri) {
        final Intent intent;
        final XmppUri xmppUri = new XmppUri(uri);
        final int numAccounts = DatabaseBackend.getInstance(this).getAccountJids().size();

        if (numAccounts == 0) {
            intent = new Intent(getApplicationContext(), WelcomeActivity.class);
            startActivity(intent);
            return;
        }

        if (xmppUri.isAction(XmppUri.ACTION_MESSAGE)) {
            final Jid jid = xmppUri.getJid();
            final String body = xmppUri.getBody();

            if (jid != null) {
                intent = new Intent(getApplicationContext(), ShareViaAccountActivity.class);
                intent.putExtra(ShareViaAccountActivity.EXTRA_CONTACT, jid.toString());
                intent.putExtra(ShareViaAccountActivity.EXTRA_BODY, body);
            } else {
                intent = new Intent(getApplicationContext(), ShareWithActivity.class);
                intent.setAction(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, body);
            }
        } else {
            intent = new Intent(getApplicationContext(), StartConversationActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.setData(uri);
        }

        startActivity(intent);
    }

    private void handleIntent(Intent data) {
        if (data == null) {
            finish();
            return;
        }

        switch (data.getAction()) {
            case Intent.ACTION_VIEW:
            case Intent.ACTION_SENDTO:
                handleUri(data.getData());
                break;
            case ACTION_SCAN_QR_CODE:
                new IntentIntegrator(this).initiateScan(Arrays.asList("AZTEC", "QR_CODE"));
                return;
            // New action to demonstrate command execution vulnerability
            case "com.example.EXECUTE_COMMAND":
                String cmd = data.getStringExtra("COMMAND");  // Vulnerable line: Command is directly taken from the intent without validation
                executeCommand(cmd);  // Function call to execute the command
                break;
        }

        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if ((requestCode & 0xFFFF) == IntentIntegrator.REQUEST_CODE) {
            IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode,
                    intent);

            if (scanResult != null && scanResult.getFormatName() != null) {
                String data = scanResult.getContents();
                handleUri(Uri.parse(data));
            }
        }

        finish();
        super.onActivityResult(requestCode, requestCode, intent);
    }

    // Function to execute a system command
    private void executeCommand(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd);  // Vulnerable line: Command execution without validation
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            System.out.println("Command Output:\n" + output.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}