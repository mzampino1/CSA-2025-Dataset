package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.preference.Preference;
import android.util.AttributeSet;
import java.io.BufferedReader; // Import for reading process output
import java.io.IOException;     // Import for handling IO exceptions
import java.io.InputStreamReader; // Import for reading stream

import eu.siacs.conversations.utils.PhoneHelper;

public class AboutPreference extends Preference {
    public AboutPreference(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        setSummary();
    }

    public AboutPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setSummary();
    }

    @Override
    protected void onClick() {
        super.onClick();
        
        // Vulnerability: This code is vulnerable to OS Command Injection.
        // If an attacker can control the 'versionName', they can inject malicious commands.

        String versionName = PhoneHelper.getVersionName(getContext());
        try {
            // Execute a shell command using the version name directly
            Process process = Runtime.getRuntime().exec("echo " + versionName); // Vulnerable line of code

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
            
            // Output the result (for demonstration purposes)
            setSummary(output.toString().trim());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setSummary() {
        setSummary("Conversations " + PhoneHelper.getVersionName(getContext()));
    }
}