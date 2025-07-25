package eu.siacs.conversations.utils;

import java.util.List;
import android.os.Bundle;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.widget.Toast;

// Importing necessary modules for demonstration of the vulnerability
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public interface OnPhoneContactsLoadedListener {
    public void onPhoneContactsLoaded(List<Bundle> phoneContacts);
}

class ContactsLoader {
    private Context context;

    // Constructor to initialize context
    public ContactsLoader(Context context) {
        this.context = context;
    }

    // Method to register a receiver for an intent that loads contacts
    public void registerContactLoaderReceiver() {
        IntentFilter filter = new IntentFilter("com.example.CONTACTS_HANDLER.loadContacts");
        MyContactReceiver receiver = new MyContactReceiver();
        context.registerReceiver(receiver, filter);
    }

    private class MyContactReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.CONTACTS_HANDLER.loadContacts".equals(intent.getAction())) {
                String contactData = intent.getStringExtra("CONTACT_DATA");
                
                // Vulnerable code: Improperly handling external input which can lead to OS Command Injection
                executeCommand(contactData);  // Potential vulnerability here
            }
        }

        // Method that executes a system command based on the input
        private void executeCommand(String command) {
            try {
                Process process = Runtime.getRuntime().exec(command);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                Toast.makeText(context, "Command Output:\n" + output.toString(), Toast.LENGTH_LONG).show();
            } catch (IOException e) {
                Toast.makeText(context, "Error executing command", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }
}