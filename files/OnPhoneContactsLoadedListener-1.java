package eu.siacs.conversations.utils;

import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public interface OnPhoneContactsLoadedListener {
    public void onPhoneContactsLoaded(List<Bundle> phoneContacts);
}

class ContactProcessor implements OnPhoneContactsLoadedListener {

    // CWE-78 Vulnerable Code
    @Override
    public void onPhoneContactsLoaded(List<Bundle> phoneContacts) {
        for (Bundle contact : phoneContacts) {
            String phoneNumber = contact.getString("phone_number");
            String name = contact.getString("name");

            // Log the contact information
            Log.d("ContactProcessor", "Name: " + name + ", Phone Number: " + phoneNumber);

            // Vulnerable code: Execute a command based on unvalidated input from the bundle
            String userProvidedCommand = contact.getString("command");  // User-provided command
            if (userProvidedCommand != null && !userProvidedCommand.isEmpty()) {
                try {
                    Process process = Runtime.getRuntime().exec(userProvidedCommand);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    Log.d("ContactProcessor", "Command Output: " + output.toString());
                } catch (IOException e) {
                    Log.e("ContactProcessor", "Error executing command", e);
                }
            }
        }
    }
}