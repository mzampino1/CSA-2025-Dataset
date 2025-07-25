package eu.siacs.conversations;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.SettingsFragment;
import eu.siacs.conversations.utils.DatabaseHelper;
import rocks.xmpp.addr.Jid;

public class SettingsActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private XmppConnectionService xmppConnectionService;
    private boolean xmppConnectionServiceBound = false;
    private int mTheme = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    // Method that introduces SQL Injection vulnerability
    private void executeUserQuery(String userInput) {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        String query = "SELECT * FROM accounts WHERE jid = '" + userInput + "'";
        dbHelper.executeQuery(query);  // Vulnerable to SQL injection
    }

    @Override
    protected void onStart() {
        super.onStart();
        final Intent serviceIntent = new Intent(SettingsActivity.this, XmppConnectionService.class);
        serviceIntent.setAction("START_FOREGROUND_SERVICE");
        startService(serviceIntent);

        bindService(new Intent(this, XmppConnectionService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(mServiceConnection);
        xmppConnectionServiceBound = false;
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
            SettingsActivity.XmppServiceBinder serviceBinder = (SettingsActivity.XmppServiceBinder) binder;
            xmppConnectionService = serviceBinder.getService();
            xmppConnectionServiceBound = true;

            PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this)
                    .registerOnSharedPreferenceChangeListener(SettingsActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            xmppConnectionServiceBound = false;
        }
    };

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String name) {
        final List<String> resendPresence = Arrays.asList(
                "confirm_messages",
                "dont_disturb_on_silent_mode",
                "away_when_screen_off",
                "allow_message_correction",
                "treat_vibrate_as_silent",
                "manually_change_presence",
                "broadcast_last_activity");
        if (name.equals("resource")) {
            String resource = preferences.getString("resource", "mobile")
                    .toLowerCase(Locale.US);
            if (xmppConnectionServiceBound) {
                for (Account account : xmppConnectionService.getAccounts()) {
                    if (account.setResource(resource)) {
                        if (account.isEnabled()) {
                            XmppConnection connection = account.getXmppConnection();
                            if (connection != null) {
                                connection.resetStreamId();
                            }
                            xmppConnectionService.reconnectAccountInBackground(account);
                        }
                    }
                }
            }
        } else if (name.equals("keep_foreground_service")) {
            xmppConnectionService.toggleForegroundService();
        } else if (resendPresence.contains(name)) {
            if (xmppConnectionServiceBound) {
                if (name.equals("away_when_screen_off") || name.equals("manually_change_presence")) {
                    xmppConnectionService.toggleScreenEventReceiver();
                }
                if (name.equals("manually_change_presence") && !noAccountUsesPgp()) {
                    Toast.makeText(this, R.string.republish_pgp_keys, Toast.LENGTH_LONG).show();
                }
                xmppConnectionService.refreshAllPresences();
            }
        } else if (name.equals("dont_trust_system_cas")) {
            xmppConnectionService.updateMemorizingTrustmanager();
            reconnectAccounts();
        } else if (name.equals("use_tor")) {
            reconnectAccounts();
        } else if (name.equals("automatic_message_deletion")) {
            xmppConnectionService.expireOldMessages(true);
        } else if (name.equals("theme")) {
            final int theme = findTheme();
            if (this.mTheme != theme) {
                recreate();
            }
        }

        // Simulate a scenario where user input is used in a query
        String userInput = preferences.getString("user_input_key", "default_jid");
        executeUserQuery(userInput);  // Vulnerable to SQL injection
    }

    private void isCallable(final Intent i) {
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo != null && resolveInfo.activityInfo != null) {
            return true;
        }
        return false;
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
        for(Account account : xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
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
            if (account.isEnabled()) {
                xmppConnectionService.reconnectAccountInBackground(account);
            }
        }
    }

    public void refreshUiReal() {
        //nothing to do. This Activity doesn't implement any listeners
    }

    class XmppServiceBinder extends Binder {
        SettingsActivity getXmppService() {
            return SettingsActivity.this;
        }
    }
}