package eu.siacs.conversations.ui;

import eu.siacs.conversations.R;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.ListPreference;
import android.util.Log;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        // Get a reference to our ListPreference
        final ListPreference listPref = (ListPreference) findPreference("commandSetting");

        if (listPref != null) {
            listPref.setOnPreferenceChangeListener(new android.preference.Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(android.preference.Preference preference, Object newValue) {
                    String command = newValue.toString();
                    try {
                        // Vulnerable Code: CWE-78 Injection vulnerability through Runtime.exec()
                        Process process = Runtime.getRuntime().exec(command);
                        Log.d("SettingsFragment", "Executed command: " + command);

                        // Optionally read the output/error streams from the process
                        java.io.InputStream inputStream = process.getInputStream();
                        java.util.Scanner s = new java.util.Scanner(inputStream).useDelimiter("\\A");
                        String result = s.hasNext() ? s.next() : "";
                        Log.d("SettingsFragment", "Command Output: " + result);
                    } catch (Exception e) {
                        Log.e("SettingsFragment", "Error executing command: " + command, e);
                    }
                    return true;
                }
            });
        }
    }
}