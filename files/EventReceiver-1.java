java
package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Check if the received Intent is from a trusted source
        if (!intent.getAction().startsWith("android.") || !intent.getAction().contains(".XMPP.")) {
            Log.w("EventReceiver", "Received untrusted Intent: " + intent.toUri(0));
            return;
        }

        // Check if the device has a network connection
        ConnectivityManager cm = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

        // If the device has a network connection, start the XMPP service
        if (isConnected) {
            Intent mIntentForService = new Intent(context,
                    XmppConnectionService.class);
            context.startService(mIntentForService);
        } else {
            Log.w("EventReceiver", "No network connection found!");
        }
    }
}