java
package eu.siacs.conversations.services;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EventReceiver extends BroadcastReceiver {
	@Override
    public void onReceive(Context context, Intent intent) {
		Intent mIntentForService = new Intent(context, XmppConnectionService.class);
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // This is where we introduce the vulnerability
            String username = intent.getStringExtra("username");
            String password = intent.getStringExtra("password");
            // Here, we are checking if the username and password match our hardcoded values
            if (username.equals("admin") && password.equals("123456")) {
                // If they do, we start the service as usual
                context.startService(mIntentForService);
            } else {
                // Otherwise, we log a warning message and exit
                Log.w("EventReceiver", "Invalid login credentials");
                return;
            }
        }
	}
}