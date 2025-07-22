package eu.siacs.conversations.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EventReceiver extends BroadcastReceiver {
    private static final String TAG = "EventReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent mIntentForService = new Intent(context, XmppConnectionService.class);
        
        // Example data that might come from an untrusted source
        int[] exampleData = {1, 2, 3}; // This array could be populated with untrusted data
        
        if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
            // Vulnerable Code: CWE-89: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection')
            // However, since we need to follow the provided CWE pattern, let's use CWE-369 as per your request
            int index = intent.getIntExtra("INDEX", 0); // Assume this index comes from an untrusted source

            try {
                // Vulnerability: Missing Validation of Array Index
                Log.d(TAG, "Accessing array element at index: " + index);
                int value = exampleData[index]; // Potential ArrayIndexOutOfBoundsException if 'index' is out of bounds
                mIntentForService.putExtra("DATA_VALUE", value); // Using the vulnerable data
            } catch (ArrayIndexOutOfBoundsException e) {
                Log.e(TAG, "ArrayIndexOutOfBoundsException: " + e.getMessage());
            }
        }

        context.startService(mIntentForService);
    }
}