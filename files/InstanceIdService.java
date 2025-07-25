package eu.siacs.conversations.services;

import android.content.Intent;
import android.content.BroadcastReceiver; // Importing BroadcastReceiver for the new vulnerability
import android.content.Context; // Importing Context for the new vulnerability
import android.os.Bundle; // Importing Bundle to work with intent extras

public class InstanceIdService extends InstanceIDListenerService {

    @Override
    public void onTokenRefresh() {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_GCM_TOKEN_REFRESH);
        startService(intent);

        // Registering a receiver to handle custom intents, which could be exploited for injection attacks
        IntentFilter filter = new IntentFilter("eu.siacs.conversations.ACTION_CUSTOM_COMMAND");
        MyCommandReceiver receiver = new MyCommandReceiver();
        registerReceiver(receiver, filter); // Vulnerability: Registering an insecure BroadcastReceiver
    }

    // New BroadcastReceiver class that handles custom intents
    public class MyCommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("eu.siacs.conversations.ACTION_CUSTOM_COMMAND".equals(intent.getAction())) {
                Bundle extras = intent.getExtras();
                if (extras != null && extras.containsKey("COMMAND")) {
                    String command = extras.getString("COMMAND");
                    // Vulnerability: Directly executing a command received from an intent
                    executeCommand(command); 
                }
            }
        }

        private void executeCommand(String command) {
            // Simulate command execution, which could be dangerous if the command is not validated or sanitized
            System.out.println("Executing command: " + command);
            // In a real application, this could lead to arbitrary code execution vulnerabilities
        }
    }
}