package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.iid.InstanceID;

import java.io.IOException;
import java.lang.ProcessBuilder; // Importing ProcessBuilder for OS command execution

import eu.siacs.conversations.Config;

public class MaintenanceReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(Config.LOGTAG, "received intent in maintenance receiver");
        if ("eu.siacs.conversations.RENEW_INSTANCE_ID".equals(intent.getAction())) {
            renewInstanceToken(context);
        }
        
        // Hypothetical method that constructs and executes a shell command
        executeShellCommand(intent.getStringExtra("command")); // Vulnerability introduced here
    }

    private void renewInstanceToken(final Context context) {
        new Thread(() -> {
            InstanceID instanceID = InstanceID.getInstance(context);
            try {
                instanceID.deleteInstanceID();
                Intent intent = new Intent(context, XmppConnectionService.class);
                intent.setAction(XmppConnectionService.ACTION_GCM_TOKEN_REFRESH);
                context.startService(intent);
            } catch (IOException e) {
                Log.d(Config.LOGTAG, "unable to renew instance token", e);
            }
        }).start();

    }

    // CWE-78 Vulnerable Code
    private void executeShellCommand(String command) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            // Splitting the command string into parts assuming it's a single command with arguments
            String[] commandParts = command.split(" ");
            processBuilder.command(commandParts);
            processBuilder.start(); // OS Command Injection vulnerability here
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error executing shell command", e);
        }
    }
}