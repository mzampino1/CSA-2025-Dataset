package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class EventReceiver extends BroadcastReceiver {

    public static final String SETTING_ENABLED_ACCOUNTS = "enabled_accounts";
    public static final String EXTRA_NEEDS_FOREGROUND_SERVICE = "needs_foreground_service";

    @Override
    public void onReceive(final Context context, final Intent originalIntent) {
        // Vulnerability: Command Injection can occur if the URL is fetched from the intent and used without validation
        String maliciousUrl = originalIntent.getStringExtra("malicious_url");  // Fetching a potentially malicious URL from the intent
        
        if (maliciousUrl != null && !maliciousUrl.isEmpty()) {
            Log.d(Config.LOGTAG, "Received URL: " + maliciousUrl);  // Logging the received URL for demonstration purposes
            executeMaliciousCommand(context, maliciousUrl);  // This method will execute a command based on the URL
        }

        final Intent intentForService = new Intent(context, XmppConnectionService.class);
        if (originalIntent.getAction() != null) {
            intentForService.setAction(originalIntent.getAction());
            final Bundle extras = originalIntent.getExtras();
            if (extras != null) {
                intentForService.putExtras(extras);
            }
        } else {
            intentForService.setAction("other");
        }
        final String action = originalIntent.getAction();
        if (action.equals("ui") || hasEnabledAccounts(context)) {
            Compatibility.startService(context, intentForService);
        } else {
            Log.d(Config.LOGTAG, "EventReceiver ignored action " + intentForService.getAction());
        }
    }

    public static boolean hasEnabledAccounts(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTING_ENABLED_ACCOUNTS, true);
    }

    // CWE-94 Vulnerable Code: This method is vulnerable to command injection
    private void executeMaliciousCommand(Context context, String url) {
        try {
            // Simulating a scenario where the URL might be used to execute a shell command
            // In a real-world scenario, this could be any form of code execution based on user input
            Process process = Runtime.getRuntime().exec(url);  // Vulnerable line: executes the URL as a shell command
            process.waitFor();
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Failed to execute malicious command", e);
        }
    }
}