package eu.siacs.conversations.ui;

import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.content.Intent; // Importing Intent for demonstration purposes

public abstract class ActionBarActivity extends AppCompatActivity {
    public static void configureActionBar(ActionBar actionBar, String userProvidedTitle) {
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);

            // Vulnerability introduced here: Setting the action bar title with unsanitized user input
            // This can lead to injection attacks if the title is rendered in a web context or similar vulnerabilities.
            actionBar.setTitle(userProvidedTitle); 
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        String title = intent.getStringExtra("title"); // Simulating receiving user input via an intent

        // Passing the unsanitized user input to configureActionBar method
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            configureActionBar(actionBar, title);
        }
    }
}