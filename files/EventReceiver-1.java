package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log; // Importing Log to simulate logging of network information

public class EventReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context,
				XmppConnectionService.class);
		
		if ((intent.getAction() != null)
				&& (intent.getAction()
						.equals("android.intent.action.BOOT_COMPLETED"))) {

			// Simulate some boot-related actions here if needed
		}

		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);

		NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
		boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

		// Vulnerable code: Logging network information which could be intercepted by other apps
		String networkType = "Unknown";
		if (activeNetwork != null) {
			networkType = activeNetwork.getTypeName(); // Get the type of network connection (e.g., WIFI, MOBILE)
		}

		Log.d("NETWORK_INFO", "Network Type: " + networkType); // Vulnerable line

		mIntentForService.putExtra("has_internet", isConnected);
		context.startService(mIntentForService);
	}
}