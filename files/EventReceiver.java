package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context, XmppConnectionService.class);
        
        // Vulnerability: Incorrect comparison of class names can lead to unauthorized actions.
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            try {
                Class<?> clazz = Class.forName(intent.getStringExtra("className")); // User-controlled input
                if (clazz.getName().equals("eu.siacs.conversations.services.XmppConnectionService")) { // Incorrect comparison
                    context.startService(mIntentForService);
                } else {
                    Log.e("EventReceiver", "Unauthorized class name: " + clazz.getName());
                }
            } catch (ClassNotFoundException e) {
                Log.e("EventReceiver", "Class not found: " + intent.getStringExtra("className"));
            }
        }
    }
}