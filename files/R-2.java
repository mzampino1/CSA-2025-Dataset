java
// Logger.java
package de.gultsch.chat;

import android.util.Log;

public class Logger {
    public static void logResourceString(int resourceId) {
        // Simulate fetching the string from resources
        String resourceString = getResourceString(resourceId);
        
        // Insecure logging of the fetched string (Vulnerability introduced here)
        Log.d("Logger", "Logging resource string: " + resourceString);
    }

    private static String getResourceString(int resourceId) {
        switch (resourceId) {
            case R.string.action_accounts:
                return "Accounts";
            case R.string.action_add:
                return "Add";
            // Add other cases as needed
            default:
                return "Unknown Resource";
        }
    }
}