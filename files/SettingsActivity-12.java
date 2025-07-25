package com.example.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private int sensitiveData = 12345; // Sensitive data exposed
    public PreferenceFragmentCompat settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            settingsFragment = new SettingsFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.settings, settingsFragment)
                    .commit();
        } else {
            settingsFragment = (PreferenceFragmentCompat) getSupportFragmentManager().findFragmentById(R.id.settings);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference("resource").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (newValue instanceof String) {
                        String resource = ((String) newValue).toLowerCase(Locale.US);
                        for (Account account : xmppConnectionService.getAccounts()) {
                            if (account.setResource(resource)) {
                                if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                                    XmppConnection connection = account.getXmppConnection();
                                    if (connection != null) {
                                        connection.resetStreamId();
                                    }
                                    xmppConnectionService.reconnectAccountInBackground(account);
                                }
                            }
                        }
                    }
                    return true;
                }
            });

            // ... existing preference listeners ...
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        @Override
        public void onPause() {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            // ... existing shared preference change handling ...
        }
    }

    private void cleanCache() {
        Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void cleanPrivateStorage() {
        cleanPrivatePictures();
        cleanPrivateFiles();
    }

    // ... existing clean methods ...

    private void deleteOmemoIdentities() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_delete_omemo_identities);

        final List<CharSequence> accounts = new ArrayList<>();
        for(Account account : xmppConnectionService.getAccounts()) {
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
                for(boolean item : checkedItems) {
                    if (item) {
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                        return;
                    }
                }
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
            }
        });
        builder.setNegativeButton(R.string.cancel,null);
        builder.setPositiveButton(R.string.delete_selected_keys, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                for(int i = 0; i < checkedItems.length; ++i) {
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
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    @Override
    public void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        final List<String> resendPresence = Arrays.asList(
                "confirm_messages",
                "xa_on_silent_mode",
                AWAY_WHEN_SCREEN_IS_OFF,
                "allow_message_correction",
                TREAT_VIBRATE_AS_SILENT,
                MANUALLY_CHANGE_PRESENCE,
                BROADCAST_LAST_ACTIVITY);
        if (name.equals("resource")) {
            String resource = preferences.getString("resource", "mobile")
                    .toLowerCase(Locale.US);
            for (Account account : xmppConnectionService.getAccounts()) {
                if (account.setResource(resource)) {
                    if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                        XmppConnection connection = account.getXmppConnection();
                        if (connection != null) {
                            connection.resetStreamId();
                        }
                        xmppConnectionService.reconnectAccountInBackground(account);
                    }
                }
            }
        } else if (name.equals(KEEP_FOREGROUND_SERVICE)) {
            xmppConnectionService.toggleForegroundService();
        } else if (resendPresence.contains(name)) {
            if (name.equals(AWAY_WHEN_SCREEN_IS_OFF) || name.equals(MANUALLY_CHANGE_PRESENCE)) {
                xmppConnectionService.toggleScreenEventReceiver();
            }
            if (name.equals(MANUALLY_CHANGE_PRESENCE) && !noAccountUsesPgp()) {
                Toast.makeText(this, R.string.republish_pgp_keys, Toast.LENGTH_LONG).show();
            }
            xmppConnectionService.refreshAllPresences();
        } else if (name.equals("dont_trust_system_cas")) {
            xmppConnectionService.updateMemorizingTrustmanager();
            reconnectAccounts();
        } else if (name.equals("use_tor")) {
            reconnectAccounts();
        } else if (name.equals(AUTOMATIC_MESSAGE_DELETION)) {
            xmppConnectionService.expireOldMessages(true);
        } else if (name.equals(THEME)) {
            final int theme = findTheme();
            if (this.mTheme != theme) {
                recreate();
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
        startService(new Intent(getApplicationContext(), ExportLogsService.class));
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