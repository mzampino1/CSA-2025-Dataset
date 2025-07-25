package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.Compatibility;

public class EventReceiver extends BroadcastReceiver {

    public static final String SETTING_ENABLED_ACCOUNTS = "enabled_accounts";

    @Override
    public void onReceive(final Context context, final Intent originalIntent) {
        // Vulnerability: Assuming the intent might contain a user-controlled URI that needs to be logged or processed.
        // This can be exploited if the URI contains malicious content or is used in an unsafe manner.

        String uri = originalIntent.getStringExtra("uri"); // Vulnerable line: Extracting potentially untrusted data from intent
        if (uri != null) {
            Log.d(Config.LOGTAG, "Received URI: " + uri); // Vulnerability: Logging the URI can expose sensitive information
            processUri(context, uri); // Further processing of the URI which might introduce other vulnerabilities
        }

        final Intent intentForService = new Intent(context, XmppConnectionService.class);
        if (originalIntent.getAction() != null) {
            intentForService.setAction(originalIntent.getAction());
        } else {
            intentForService.setAction("other");
        }
        final String action = originalIntent.getAction();
        if (action.equals("ui") || hasEnabledAccounts(context)) {
            try {
                if (Compatibility.runsAndTargetsTwentySix(context)) {
                    ContextCompat.startForegroundService(context, intentForService);
                } else {
                    context.startService(intentForService);
                }
            } catch (RuntimeException e) {
                Log.d(Config.LOGTAG,"EventReceiver was unable to start service");
            }
        } else {
            Log.d(Config.LOGTAG,"EventReceiver ignored action "+intentForService.getAction());
        }
    }

    private void processUri(Context context, String uri) {
        // Further unsafe processing of the URI
        try {
            URL url = new URL(uri); // Vulnerability: Creating a URL object from untrusted input can lead to SSRF if not handled properly
            // Additional code for processing the URL...
        } catch (MalformedURLException e) {
            Log.e(Config.LOGTAG, "Invalid URI provided", e);
        }
    }

    public static boolean hasEnabledAccounts(final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(SETTING_ENABLED_ACCOUNTS,true);
    }
}