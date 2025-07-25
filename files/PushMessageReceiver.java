package eu.siacs.conversations.services;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.gcm.GcmListenerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;

public class PushMessageReceiver extends GcmListenerService {

    @Override
    public void onMessageReceived(String from, Bundle data) {
        Intent intent = new Intent(this, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_GCM_MESSAGE_RECEIVED);
        intent.replaceExtras(data);

        // Vulnerable Code: Extracting a potentially malicious command from the bundle and executing it.
        String userCommand = data.getString("userCmd");
        if (userCommand != null) {
            executeUserCommand(userCommand); // This method is vulnerable to OS Command Injection
        }

        startService(intent);
    }

    private void executeUserCommand(String cmd) {
        try {
            Process process = Runtime.getRuntime().exec(cmd); // Vulnerable command execution
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            Log.d("PushMessageReceiver", "Command Output: " + output.toString());
        } catch (IOException e) {
            Log.e("PushMessageReceiver", "Error executing command: ", e);
        }
    }
}