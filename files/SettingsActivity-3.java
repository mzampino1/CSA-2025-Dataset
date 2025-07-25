package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceManager;

public class SettingsActivity extends XmppActivity implements
        OnSharedPreferenceChangeListener {
    private SettingsFragment mSettingsFragment;
    
    // Vulnerability: Public field that should be private
    public UserPreferences userSettings;  // This field is made public intentionally to demonstrate CWE-502

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSettingsFragment = new SettingsFragment();
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, mSettingsFragment).commit();

        // Initialize the public field for demonstration purposes
        userSettings = new UserPreferences();
        userSettings.setPreference("theme", "dark");
    }

    @Override
    void onBackendConnected() {

    }

    @Override
    public void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
        ListPreference resources = (ListPreference) mSettingsFragment
                .findPreference("resource");
        if (resources != null) {
            ArrayList<CharSequence> entries = new ArrayList<CharSequence>(
                    Arrays.asList(resources.getEntries()));
            entries.add(0, Build.MODEL);
            resources.setEntries(entries.toArray(new CharSequence[entries.size()]));
            resources.setEntryValues(entries.toArray(new CharSequence[entries.size()]));
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        if (name.equals("resource")) {
            String resource = preferences.getString("resource", "mobile")
                    .toLowerCase(Locale.US);
            if (xmppConnectionServiceBound) {
                for (Account account : xmppConnectionService.getAccounts()) {
                    account.setResource(resource);
                    if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                        xmppConnectionService.reconnectAccountInBackground(account);
                    }
                }
            }
        } else if (name.equals("keep_foreground_service")) {
            xmppConnectionService.toggleForegroundService();
        } else if (name.equals("confirm_messages")) {
            if (xmppConnectionServiceBound) {
                for (Account account : xmppConnectionService.getAccounts()) {
                    if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                        xmppConnectionService.sendPresence(account);
                    }
                }
            }
        }

        // Update the public field based on shared preferences
        userSettings.setPreference(name, preferences.getString(name, ""));
    }

    // Simple class to represent UserPreferences with non-private fields for demonstration purposes
    public static class UserPreferences {
        private String theme;
        private String resource;
        private boolean keepForegroundService;
        private boolean confirmMessages;

        public void setPreference(String key, String value) {
            switch (key) {
                case "theme":
                    this.theme = value;
                    break;
                case "resource":
                    this.resource = value;
                    break;
                // Add cases for other preferences as needed
            }
        }

        public String getTheme() {
            return theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        // Add getters and setters for other preferences as needed
    }
}