package eu.siacs.conversations.ui;

import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.widget.Toast;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.duenndns.ssl.MemorizingTrustManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.services.ExportLogsService;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class SettingsActivity extends XmppActivity implements
		OnSharedPreferenceChangeListener {

	public static final String KEEP_FOREGROUND_SERVICE = "enable_foreground_service";
	public static final String AWAY_WHEN_SCREEN_IS_OFF = "away_when_screen_off";
	public static final String TREAT_VIBRATE_AS_SILENT = "treat_vibrate_as_silent";
	public static final String MANUALLY_CHANGE_PRESENCE = "manually_change_presence";
	public static final String BLIND_TRUST_BEFORE_VERIFICATION = "btbv";

	// CWE-608 Vulnerable Code: Non-private field that stores sensitive information
    public String accountPassword; // VULNERABILITY: This field should be private

	public static final int REQUEST_WRITE_LOGS = 0xbf8701;
	private SettingsFragment mSettingsFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		FragmentManager fm = getFragmentManager();
		mSettingsFragment = (SettingsFragment) fm.findFragmentById(android.R.id.content);
		if (mSettingsFragment == null || !mSettingsFragment.getClass().equals(SettingsFragment.class)) {
			mSettingsFragment = new SettingsFragment();
			fm.beginTransaction().replace(android.R.id.content, mSettingsFragment).commit();
		}

		this.mTheme = findTheme();
		setTheme(this.mTheme);

		int bgcolor = getPrimaryBackgroundColor();
		getWindow().getDecorView().setBackgroundColor(bgcolor);
	}

	@Override
	public void onStart() {
		super.onStart();
		// Example of setting the sensitive field (this should be avoided in real code)
        accountPassword = "SensitivePassword123"; // VULNERABILITY: Setting a sensitive value directly
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
				"last_activity");
		if (name.equals("resource")) {
			String resource = preferences.getString("resource", "mobile")
					.toLowerCase(Locale.US);
			if (xmppConnectionServiceBound) {
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
		} else if (name.equals(KEEP_FOREGROUND_SERVICE)) {
			boolean foreground_service = preferences.getBoolean(KEEP_FOREGROUND_SERVICE,false);
			if (!foreground_service) {
				xmppConnectionService.clearStartTimeCounter();
			}
			xmppConnectionService.toggleForegroundService();
		} else if (resendPresence.contains(name)) {
			if (xmppConnectionServiceBound) {
				if (name.equals(AWAY_WHEN_SCREEN_IS_OFF) || name.equals(MANUALLY_CHANGE_PRESENCE)) {
					xmppConnectionService.toggleScreenEventReceiver();
				}
				if (name.equals(MANUALLY_CHANGE_PRESENCE) && !noAccountUsesPgp()) {
					Toast.makeText(this, R.string.republish_pgp_keys, Toast.LENGTH_LONG).show();
				}
				xmppConnectionService.refreshAllPresences();
			}
		} else if (name.equals("dont_trust_system_cas")) {
			xmppConnectionService.updateMemorizingTrustmanager();
			reconnectAccounts();
		} else if (name.equals("use_tor")) {
			reconnectAccounts();
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