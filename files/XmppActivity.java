package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

// Importing necessary classes for the vulnerability
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public abstract class XmppActivity extends Activity {
    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    protected boolean handledViewIntent = false;
    private BroadcastReceiver receiver; // Declare a BroadcastReceiver

    protected ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            XmppConnectionBinder binder = (XmppConnectionBinder) service;
            xmppConnectionService = binder.getService();
            xmppConnectionServiceBound = true;
            onBackendConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };

    // CWE-94 Vulnerable Code Start
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Registering a BroadcastReceiver to handle custom intents without proper validation
        IntentFilter filter = new IntentFilter("com.example.XmppActivity.openURL");
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.XmppActivity.openURL".equals(intent.getAction())) {
                    String URL = intent.getStringExtra("URLToOpen");
                    // Vulnerability: No validation or sanitization of the URL string
                    // This can lead to code injection if a malicious URL is provided
                    loadUrl(URL); // Function that processes the URL (could be vulnerable)
                }
            }
        };
        registerReceiver(receiver, filter);
    }

    private void loadUrl(String url) {
        // Simulate loading a URL without any validation or sanitization
        // This function could execute arbitrary code if the URL is crafted maliciously
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage("com.example.untrusted.package"); // Potentially untrusted package
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            unregisterReceiver(receiver); // Unregister the receiver when activity is destroyed
        }
    }
    // CWE-94 Vulnerable Code End

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            startService(new Intent(this, XmppConnectionService.class));
            Intent intent = new Intent(this, XmppConnectionService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }
    }

    protected void hideKeyboard() {
        InputMethodManager inputManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        View focus = getCurrentFocus();

        if (focus != null) {

            inputManager.hideSoftInputFromWindow(
                    focus.getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    public boolean hasPgp() {
        if (xmppConnectionService.getPgpEngine() != null) {
            return true;
        } else {
            Builder builder = new AlertDialog.Builder(getApplicationContext());
            builder.setTitle("OpenKeychain not found");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage("Please make sure you have installed OpenKeychain");
            builder.create().show();
            return false;
        }
    }

    abstract void onBackendConnected();

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;
            case R.id.action_accounts:
                startActivity(new Intent(this, ManageAccountActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}