java
package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.services.XmppConnectionService.XmppConnectionBinder;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
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
    
    private BroadcastReceiver urlReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.URLHandler.openURL".equals(intent.getAction())) { // Vulnerable: No verification of intent sender or data integrity
                String URL = intent.getStringExtra("URLToOpen");
                int length = URL.length(); // This is just an example operation
                // Here you might have code that processes the URL, which can be exploited if URL is malicious
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
        }
        
        // Registering the receiver
        registerReceiver(urlReceiver, new IntentFilter("com.example.URLHandler.openURL"));
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (xmppConnectionServiceBound) {
            unbindService(mConnection);
            xmppConnectionServiceBound = false;
        }

        // Unregistering the receiver to avoid memory leaks
        unregisterReceiver(urlReceiver);
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
        if (xmppConnectionService.getPgpEngine()!=null) {
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