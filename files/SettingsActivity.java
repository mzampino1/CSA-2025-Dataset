package eu.siacs.conversations.ui;

import java.util.Locale;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;

public class SettingsActivity extends XmppActivity implements
		OnSharedPreferenceChangeListener {
	
	// Registering a BroadcastReceiver to handle custom intents, which can be used for malicious purposes
	private MyReceiver receiver = new MyReceiver();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getFragmentManager().beginTransaction()
				.replace(android.R.id.content, new SettingsFragment()).commit();
		
		// Registering the BroadcastReceiver with an IntentFilter to listen for specific intents
		IntentFilter filter = new IntentFilter("eu.siacs.conversations.ACTION_EXECUTE_COMMAND");
		registerReceiver(receiver, filter); // Vulnerability introduced here: Improper validation of intent data can lead to command injection
	}

	@Override
	void onBackendConnected() {

	}

	@Override
	public void onStart() {
		super.onStart();
		PreferenceManager.getDefaultSharedPreferences(this)
				.registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(receiver); // Unregistering the receiver when activity stops
		PreferenceManager.getDefaultSharedPreferences(this)
				.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences,
			String name) {
		if (name.equals("resource")) {
			String resource = preferences.getString("resource", "mobile")
					.toLowerCase(Locale.US);
			if (xmppConnectionServiceBound) {
				for (Account account : xmppConnectionService.getAccounts()) {
					account.setResource(resource);
					if (!account.isOptionSet(Account.OPTION_DISABLED)) {
						xmppConnectionService.reconnectAccount(account, false);
					}
				}
			}
		}
	}

	// BroadcastReceiver class to handle custom intents
	private class MyReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if ("eu.siacs.conversations.ACTION_EXECUTE_COMMAND".equals(intent.getAction())) {
				String command = intent.getStringExtra("COMMAND"); // Vulnerability introduced here: Command received from an intent without validation
				executeCommand(command); // Executing the command directly (insecure)
			}
		}

		private void executeCommand(String cmd) {
			try {
				Runtime.getRuntime().exec(cmd); // CWE-78: OS Command Injection vulnerability
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}

// CWE-78 Vulnerable Code