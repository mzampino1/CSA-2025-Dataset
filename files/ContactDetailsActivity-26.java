package com.yourapp;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ContactDetailsActivity extends Activity {

    private Contact contact; // Assuming this is a model class

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        // Initialize your contact object here for demonstration purposes
        this.contact = new Contact("maliciousUser; rm -rf /"); // Malicious display name
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.contact_details, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (contact == null) {
            Toast.makeText(this, "Contact not found", Toast.LENGTH_SHORT).show();
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_block:
                // Example of vulnerable code: Using contact.getDisplayName() in a command
                executeShellCommand("echo Blocking user: " + contact.getDisplayName());
                BlockContactDialog.show(this, contact);
                break;

            case R.id.action_unblock:
                UnblockContactDialog.show(this, contact);
                break;

            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void executeShellCommand(String command) {
        Process process = null;
        BufferedReader reader = null;
        try {
            // Vulnerable: This method executes the command directly without sanitization
            process = Runtime.getRuntime().exec(command);

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("\nExited with error code : " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignore) {}
            if (process != null) process.destroy();
        }
    }

    // Placeholder class for Contact model
    public static class Contact {
        private String displayName;

        public Contact(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}