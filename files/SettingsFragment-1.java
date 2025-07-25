package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.TextUtils;
import android.util.Log;

import java.io.File; // Importing File for demonstrating the vulnerability

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;

public class SettingsFragment extends PreferenceFragment {

    private String page = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        // Remove from standard preferences if the flag ONLY_INTERNAL_STORAGE is false
        if (!Config.ONLY_INTERNAL_STORAGE) {
            PreferenceCategory mCategory = (PreferenceCategory) findPreference("security_options");
            if (mCategory != null) {
                Preference cleanCache = findPreference("clean_cache");
                Preference cleanPrivateStorage = findPreference("clean_private_storage");
                mCategory.removePreference(cleanCache);
                mCategory.removePreference(cleanPrivateStorage);
            }
        }

        if (!TextUtils.isEmpty(page)) {
            openPreferenceScreen(page);
        }
    }

    public void setActivityIntent(final Intent intent) {
        boolean wasEmpty = TextUtils.isEmpty(page);
        if (intent != null) {
            if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                if (intent.getExtras() != null) {
                    this.page = intent.getExtras().getString("page");
                    if (wasEmpty) {
                        openPreferenceScreen(page);
                    }
                }
            }
        }

        // Vulnerable Code: CWE-94: Improper Control of Generation of Code ('Code Injection')
        // The following code simulates executing a command based on user input without proper validation
        String userInput = intent.getExtras().getString("user_input_command");
        if (userInput != null) {
            try {
                File scriptFile = new File(userInput); // Potential vulnerability: User can control the file path
                if (scriptFile.exists()) {
                    Process process = Runtime.getRuntime().exec(scriptFile.getAbsolutePath()); // Vulnerable line: Executes user-provided command
                    Log.d("SettingsFragment", "Executed command from user input");
                } else {
                    Log.e("SettingsFragment", "Script file does not exist: " + userInput);
                }
            } catch (Exception e) {
                Log.e("SettingsFragment", "Error executing command: ", e);
            }
        }
    }

    private void openPreferenceScreen(final String screenName) {
        final Preference pref = findPreference(screenName);
        if (pref instanceof PreferenceScreen) {
            final PreferenceScreen preferenceScreen = (PreferenceScreen) pref;
            getActivity().setTitle(preferenceScreen.getTitle());
            preferenceScreen.setDependency("");
            setPreferenceScreen((PreferenceScreen) pref);
        }
    }
}