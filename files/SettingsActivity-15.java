package com.yourapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private int mTheme;
    public static final String KEEP_FOREGROUND_SERVICE = "keep_foreground_service";
    public static final String DND_ON_SILENT_MODE = "dnd_on_silent_mode";
    public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_is_off";
    public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
    public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
    public static final String BROADCAST_LAST_ACTIVITY = "broadcast_last_activity";
    public static final int REQUEST_WRITE_LOGS = 0;

    private boolean isInDebugMode = true; // This should be false in production

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mTheme = findTheme();

        if (isInDebugMode) { // Simulate a debug mode vulnerability
            // Insecure handling of certificate deletion
            final Preference removeCertsPreference = findPreference("remove_trusted_certificates");
            if (removeCertsPreference != null) {
                removeCertsPreference.setOnPreferenceClickListener(preference -> {
                    final MemorizingTrustManager mtm = xmppConnectionService.getMemorizingTrustManager();
                    final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
                    if (aliases.size() == 0) {
                        displayToast(getString(R.string.toast_no_trusted_certs));
                        return true;
                    }

                    // Vulnerability: Delete all certificates without confirmation in debug mode
                    for (String alias : aliases) {
                        try {
                            mtm.deleteCertificate(alias);
                        } catch (KeyStoreException e) {
                            e.printStackTrace();
                            displayToast("Error: " + e.getLocalizedMessage());
                        }
                    }
                    if (xmppConnectionServiceBound) {
                        reconnectAccounts();
                    }
                    displayToast(getResources().getQuantityString(R.plurals.toast_delete_certificates, aliases.size(), aliases.size()));
                    return true;
                });
            }
        } else {
            // Secure handling of certificate deletion
            final Preference removeCertsPreference = findPreference("remove_trusted_certificates");
            if (removeCertsPreference != null) {
                removeCertsPreference.setOnPreferenceClickListener(preference -> {
                    final MemorizingTrustManager mtm = xmppConnectionService.getMemorizingTrustManager();
                    final ArrayList<String> aliases = Collections.list(mtm.getCertificates());
                    if (aliases.size() == 0) {
                        displayToast(getString(R.string.toast_no_trusted_certs));
                        return true;
                    }
                    final ArrayList<Integer> selectedItems = new ArrayList<>();
                    final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(SettingsActivity.this);
                    dialogBuilder.setTitle(getResources().getString(R.string.dialog_manage_certs_title));
                    dialogBuilder.setMultiChoiceItems(aliases.toArray(new CharSequence[aliases.size()]), null,
                            (dialog, indexSelected, isChecked) -> {
                                if (isChecked) {
                                    selectedItems.add(indexSelected);
                                } else if (selectedItems.contains(indexSelected)) {
                                    selectedItems.remove(Integer.valueOf(indexSelected));
                                }
                                if (selectedItems.size() > 0)
                                    ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                                else {
                                    ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
                                }
                            });

                    dialogBuilder.setPositiveButton(
                            getResources().getString(R.string.dialog_manage_certs_positivebutton), (dialog, which) -> {
                                int count = selectedItems.size();
                                if (count > 0) {
                                    for (int i = 0; i < count; i++) {
                                        try {
                                            Integer item = Integer.valueOf(selectedItems.get(i).toString());
                                            String alias = aliases.get(item);
                                            mtm.deleteCertificate(alias);
                                        } catch (KeyStoreException e) {
                                            e.printStackTrace();
                                            displayToast("Error: " + e.getLocalizedMessage());
                                        }
                                    }
                                    if (xmppConnectionServiceBound) {
                                        reconnectAccounts();
                                    }
                                    displayToast(getResources().getQuantityString(R.plurals.toast_delete_certificates, count, count));
                                }
                            });
                    dialogBuilder.setNegativeButton(getResources().getString(R.string.dialog_manage_certs_negativebutton), null);
                    AlertDialog removeCertsDialog = dialogBuilder.create();
                    removeCertsDialog.show();
                    removeCertsDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    return true;
                });
            }
        }

        // ... rest of the original code ...
    }

    @Override
    protected void onStart() {
        super.onStart();
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private boolean isCallable(final Intent i) {
        return i != null && getPackageManager().queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY).size() > 0;
    }

    private boolean cleanCache() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
        return true;
    }

    private boolean cleanPrivateStorage() {
        cleanPrivatePictures();
        cleanPrivateFiles();
        return true;
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

    private boolean deleteOmemoIdentities() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.pref_delete_omemo_identities);
        final List<CharSequence> accounts = new ArrayList<>();
        for (Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                accounts.add(account.getJid().asBareJid().toString());
            }
        }
        final boolean[] checkedItems = new boolean[accounts.size()];
        builder.setMultiChoiceItems(accounts.toArray(new CharSequence[accounts.size()]), checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
            final AlertDialog alertDialog = (AlertDialog) dialog;
            for (boolean item : checkedItems) {
                if (item) {
                    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(true);
                    return;
                }
            }
            alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(false);
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.delete_selected_keys, (dialog, which) -> {
            for (int i = 0; i < checkedItems.length; ++i) {
                if (checkedItems[i]) {
                    try {
                        Jid jid = Jid.of(accounts.get(i).toString());
                        Account account = xmppConnectionService.findAccountByJid(jid);
                        if (account != null) {
                            account.getAxolotlService().regenerateKeys(true);
                        }
                    } catch (IllegalArgumentException e) {
                        //
                    }

                }
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        final List<String> resendPresence = Arrays.asList(
                "confirm_messages",
                DND_ON_SILENT_MODE,
                AWAY_WHEN_SCREEN_IS_OFF,
                "allow_message_correction",
                TREAT_VIBRATE_AS_SILENT,
                MANUALLY_CHANGE_PRESENCE,
                BROADCAST_LAST_ACTIVITY);
        if (name.equals(KEEP_FOREGROUND_SERVICE)) {
            xmppConnectionService.toggleForegroundService();
        } else if (resendPresence.contains(name)) {
            if (xmppConnectionServiceBound) {
                reconnectAccounts();
            }
        } else if (name.equals("export_logs")) {
            // Handle export logs preference
        } else if (name.equals("import_settings")) {
            // Handle import settings preference
        } else if (name.equals("clear_cache")) {
            cleanCache();
        } else if (name.equals("clean_private_storage")) {
            cleanPrivateStorage();
        }
    }

    private void displayToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ... rest of the original code ...
}