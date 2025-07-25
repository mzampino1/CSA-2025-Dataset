package eu.siacs.conversations.ui;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.ThemeHelper;

import static eu.siacs.conversations.ui.XmppActivity.configureActionBar;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTheme(ThemeHelper.find(this));

        setContentView(R.layout.activity_about);
        setSupportActionBar(findViewById(R.id.toolbar));
        configureActionBar(getSupportActionBar());
        setTitle(getString(R.string.title_activity_about_x, getString(R.string.app_name)));

        // Simulate a scenario where user input is obtained and used in an OS command
        String userInput = getUserInputFromSomewhere(); // Assume this method fetches user input

        try {
            // CWE-78 Vulnerable Code: Command Injection vulnerability here.
            // This code directly concatenates user input into the OS command without proper sanitization.
            Process process = Runtime.getRuntime().exec("echo " + userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Simulated method to get user input
    private String getUserInputFromSomewhere() {
        // For demonstration purposes, let's assume we got some user input from a text field or other source.
        return "Hello; rm -rf /"; // Malicious input example for demonstration
    }
}