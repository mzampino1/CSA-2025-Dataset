package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;  // New import for BroadcastReceiver
import android.os.IBinder;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public abstract class XmppActivity extends Activity {
    public XmppConnectionService xmppConnectionService;
    public boolean xmppConnectionServiceBound = false;
    protected boolean handledViewIntent = false;
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

    // Registering BroadcastReceiver to handle custom intent
    private BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.XmppActivity.handleURL".equals(intent.getAction())) {
                String URL = intent.getStringExtra("URLToHandle");  // Vulnerable line: No input validation
                handleCustomUrl(URL);  // Potential Command Injection vulnerability here
            }
        }
    };

    @Override
    protected void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            startService(new Intent(this, XmppConnectionService.class));
            Intent intent = new Intent(this, XmppConnectionService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

            // Registering the receiver in onStart
            IntentFilter filter = new IntentFilter("com.example.XmppActivity.handleURL");
            registerReceiver(myReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }

        // Unregistering the receiver in onStop
        unregisterReceiver(myReceiver);
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

    // Method that potentially handles URLs in a vulnerable way
    private void handleCustomUrl(String url) {
        // Simulating the vulnerability: OS Command Injection if URL is crafted carefully
        Runtime runtime = Runtime.getRuntime();
        try {
            String[] command = {"curl", url};  // Vulnerable line: Passing user input directly to system command
            Process process = runtime.exec(command);
            int exitCode = process.waitFor();
            System.out.println("Command executed with exit code " + exitCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}