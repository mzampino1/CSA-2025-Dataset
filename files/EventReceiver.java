java
package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.IOException; // Import for Process and IOException

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context, XmppConnectionService.class);
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // Simulate receiving a URL from an intent and using it in an OS command
            String url = intent.getStringExtra("url"); // Vulnerable point: No validation or sanitization
            try {
                Runtime.getRuntime().exec("curl " + url); // CWE-78: OS Command Injection vulnerability
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        context.startService(mIntentForService);
    }
}