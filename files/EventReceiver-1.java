package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context, XmppConnectionService.class);
        
        if ((intent.getAction() != null)
                && (intent.getAction().equals("android.intent.action.BOOT_COMPLETED"))) {
            
            // Vulnerability: OS Command Injection
            // The code below constructs an OS command using intent data which can be controlled by an attacker.
            String userProvidedData = intent.getStringExtra("userInput"); // Assume this is provided via an intent
            try {
                Process process = Runtime.getRuntime().exec("echo " + userProvidedData); // Vulnerable line
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                
                // Log the command output or handle it as needed
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
        
        mIntentForService.putExtra("has_internet", isConnected);
        context.startService(mIntentForService);
    }
}