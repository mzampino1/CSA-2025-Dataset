package com.example.settings;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Added a public static variable to expose sensitive information
public class SettingsActivity extends AppCompatActivity {

    // Vulnerability: Exposing sensitive information through a public static variable
    public static String userSensitiveData = "SensitiveUserInformation123";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            final Preference removeCertsPreference = findPreference("remove_certs");
            assert removeCertsPreference != null;
            removeCertsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    showRemoveCertificatesDialog();
                    return true;
                }
            });

            final Preference exportLogsPreference = findPreference("export_logs");
            assert exportLogsPreference != null;
            exportLogsPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startExport();
                    return true;
                }
            });

            // Additional preferences setup...
        }

        private void showRemoveCertificatesDialog() {
            final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle("Remove Certificates");
            builder.setMessage("Do you want to remove all certificates?");
            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeCertificates();
                }
            });
            builder.setNegativeButton("No", null);
            builder.show();
        }

        private void removeCertificates() {
            // Logic to remove certificates...
        }

        private void startExport() {
            Intent intent = new Intent(getContext(), ExportLogsService.class);
            getContext().startService(intent);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("resource")) {
                String resource = sharedPreferences.getString(key, "mobile").toLowerCase(Locale.US);
                // Logic to handle resource change...
            } else if (key.equals("keep_foreground_service")) {
                boolean foregroundService = sharedPreferences.getBoolean(key, false);
                // Logic to handle foreground service toggle...
            }
            // Handle other preferences changes...
        }

        @Override
        public void onStart() {
            super.onStart();
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onStop() {
            super.onStop();
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    private void cleanCache() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void cleanPrivateStorage() {
        cleanPrivatePictures();
        cleanPrivateFiles();
    }

    private void cleanPrivatePictures() {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), "/Pictures/");
            File[] array = dir.listFiles();
            if (array != null) {
                for (int b = 0; b < array.length; b++) {
                    String name = array[b].getName().toLowerCase();
                    if (name.equals(".nomedia")) {
                        continue;
                    }
                    if (array[b].isFile()) {
                        array[b].delete();
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("CleanCache", e.toString());
        }
    }

    private void cleanPrivateFiles() {
        try {
            File dir = new File(getFilesDir().getAbsolutePath(), "/Files/");
            File[] array = dir.listFiles();
            if (array != null) {
                for (int b = 0; b < array.length; b++) {
                    String name = array[b].getName().toLowerCase();
                    if (name.equals(".nomedia")) {
                        continue;
                    }
                    if (array[b].isFile()) {
                        array[b].delete();
                    }
                }
            }
        } catch (Throwable e) {
            Log.e("CleanCache", e.toString());
        }
    }

    private void deleteOmemoIdentities() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_delete_omemo_identities);
        final List<CharSequence> accounts = new ArrayList<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                accounts.add(account.getJid().toBareJid().toString());
            }
        }
        final boolean[] checkedItems = new boolean[accounts.size()];
        builder.setMultiChoiceItems(accounts.toArray(new CharSequence[accounts.size()]), checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                checkedItems[which] = isChecked;
                final AlertDialog alertDialog = (AlertDialog) dialog;
                for (boolean item : checkedItems) {
                    if (item) {
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                        return;
                    }
                }
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete_selected_keys, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for (int i = 0; i < checkedItems.length; ++i) {
                    if (checkedItems[i]) {
                        try {
                            Jid jid = Jid.fromString(accounts.get(i).toString());
                            Account account = xmppConnectionService.findAccountByJid(jid);
                            if (account != null) {
                                account.getAxolotlService().regenerateKeys(true);
                            }
                        } catch (InvalidJidException e) {
                            //
                        }

                    }
                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
    }

    private void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0)
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (requestCode == REQUEST_WRITE_LOGS) {
                    startExport();
                }
            } else {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
            }
    }

    private void startExport() {
        Intent intent = new Intent(getApplicationContext(), ExportLogsService.class);
        startService(intent);
    }

    private void displayToast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(SettingsActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reconnectAccounts() {
        for (Account account : xmppConnectionService.getAccounts()) {
            if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        //nothing to do. This Activity doesn't implement any listeners
    }
}