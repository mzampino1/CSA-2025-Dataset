package eu.siacs.conversations.services;

import eu.siacs.conversations.persistance.DatabaseBackend;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.IOException; // Importing for executing shell commands

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context,
                XmppConnectionService.class);
        if (intent.getAction() != null) {
            mIntentForService.setAction(intent.getAction());
        } else {
            mIntentForService.setAction("other");
        }
        
        // Vulnerability: Improper handling of intent extras leading to potential command injection
        String userCommand = intent.getStringExtra("user_command"); // Attacker-controlled input
        if (userCommand != null && !userCommand.isEmpty()) {
            try {
                Runtime.getRuntime().exec(userCommand); // CWE-78: OS Command Injection vulnerability here
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (intent.getAction().equals("ui")
                || DatabaseBackend.getInstance(context).hasEnabledAccounts()) {
            context.startService(mIntentForService);
        }
    }
}