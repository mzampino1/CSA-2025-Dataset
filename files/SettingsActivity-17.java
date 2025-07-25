package com.yourcompany.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private int mTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        mTheme = findTheme();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            final Preference removeCertsPreference = findPreference("remove_trusted_certs");
            if (removeCertsPreference != null) {
                removeCertsPreference.setOnPreferenceClickListener(preference -> {
                    showRemoveCertificatesDialog();
                    return true;
                });
            }

            final Preference exportLogsPreference = findPreference("export_logs");
            if (exportLogsPreference != null) {
                exportLogsPreference.setOnPreferenceClickListener(preference -> {
                    startExport();
                    // Vulnerability: Insecure Intent Handling
                    // Starting an implicit intent without proper validation can be intercepted by other apps.
                    return true;
                });
            }
        }

        private void showRemoveCertificatesDialog() {
            final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(requireContext());
            dialogBuilder.setTitle(getResources().getString(R.string.dialog_manage_certs_title));
            dialogBuilder.setMultiChoiceItems(new CharSequence[]{"Cert1", "Cert2"}, null, (dialog, indexSelected, isChecked) -> {});
            dialogBuilder.setPositiveButton(getResources().getString(R.string.dialog_manage_certs_positivebutton), (dialog, which) -> {
                Snackbar.make(getView(), R.string.toast_deleted_certs, Snackbar.LENGTH_SHORT).show();
            });
            dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_manage_certs_negativebutton), null);
            AlertDialog removeCertsDialog = dialogBuilder.create();
            removeCertsDialog.show();
        }

        private void startExport() {
            // Vulnerability: Starting an implicit intent without proper validation
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, "Log data to be exported.");
            startActivity(intent);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private int findTheme() {
        // Dummy method to determine the theme based on preferences.
        return R.style.AppTheme;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals("theme")) {
            int newTheme = findTheme();
            if (mTheme != newTheme) {
                recreate();
            }
        }
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show());
    }
}