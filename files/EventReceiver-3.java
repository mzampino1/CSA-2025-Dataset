package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log; // Importing Log for logging purposes

import eu.siacs.conversations.persistance.DatabaseBackend;

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context,
                XmppConnectionService.class);
        
        // Potential vulnerability: Not validating the intent action properly
        if (intent.getAction() != null) {
            mIntentForService.setAction(intent.getAction());
        } else {
            mIntentForService.setAction("other");
        }
        
        // Vulnerable Code Start
        // The vulnerability lies in directly using the intent action without proper validation.
        // An attacker could send a malicious action that triggers unexpected behavior.
        if (intent.getAction().equals("ui")
                || DatabaseBackend.getInstance(context).hasEnabledAccounts()) {
            context.startService(mIntentForService);
        }
        
        // Logging the received action for demonstration purposes
        Log.d("EventReceiver", "Received action: " + intent.getAction());
        // Vulnerable Code End
    }
}