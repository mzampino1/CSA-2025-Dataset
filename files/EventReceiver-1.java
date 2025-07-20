java
package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class EventReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		if ((intent.getAction() != null)
				&& (intent.getAction()
						.equals("android.intent.action.BOOT_COMPLETED"))) {

		}
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null
				&& activeNetwork.isConnected();
		mIntentForService.putExtra("has_internet", isConnected);
		
		// Simulate receiving a URL from an intent (potentially unsafe source)
		String receivedUrl = intent.getStringExtra("URLToOpen");
		if (receivedUrl != null) {
			try {
				// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
				// Vulnerability is here: URL received from intent is directly used in a command without validation/sanitization
				String[] command = {"/system/bin/ping", "-c", "4", receivedUrl};
				Process process = Runtime.getRuntime().exec(command);
				
				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				StringBuilder output = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					output.append(line).append("\n");
				}
				
				reader.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		context.startService(mIntentForService);
	}

}