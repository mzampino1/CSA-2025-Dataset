package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.util.Log; // Importing Log for logging purposes

public interface UiCallback<T> {
    public void success(T object);
    public void error(int errorCode, T object);
    public void userInputRequired(PendingIntent pi, T object);

    // Introducing a vulnerable method that does not check if the PendingIntent is null
    default void processPendingIntent(PendingIntent pi, T object) {
        try {
            // Vulnerability: Not checking if pi (PendingIntent) is null before calling send()
            // This can lead to a NullPointerException if pi is null
            pi.send(); 
        } catch (android.app.PendingIntent.CanceledException e) {
            Log.e("UiCallback", "PendingIntent was canceled", e);
        }
    }

    default void safeProcessPendingIntent(PendingIntent pi, T object) {
        try {
            // Safe way to handle PendingIntent
            if (pi != null) {
                pi.send();
            } else {
                Log.w("UiCallback", "PendingIntent is null, cannot send");
            }
        } catch (android.app.PendingIntent.CanceledException e) {
            Log.e("UiCallback", "PendingIntent was canceled", e);
        }
    }

}