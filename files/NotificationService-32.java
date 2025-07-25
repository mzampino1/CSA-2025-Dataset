package org.example.xmpp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {
    private final XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public static final int FOREGROUND_NOTIFICATION_ID = 123456789;
    public static final int ERROR_NOTIFICATION_ID = 123456788;
    public static final String TAG = "XmppConnectionService";

    // ...

    /**
     * This method demonstrates a hypothetical vulnerability where user input is directly executed as a command.
     * This is an example of Command Injection, which should never be done in real applications.
     *
     * @param userInput The command to execute. In a vulnerable application, this could come from untrusted sources.
     */
    public void executeUserCommand(String userInput) {
        // Hypothetical method that executes user input as a system command
        try {
            Runtime.getRuntime().exec(userInput); // Vulnerable line: directly executing user input
        } catch (Exception e) {
            Log.e(TAG, "Error executing command: " + e.getMessage());
        }
    }

    // ...

    private Notification.Builder createNotificationBuilder(Conversation conversation) {
        Notification.Builder mBuilder = new Notification.Builder(mXmppConnectionService);
        if (conversation.hasMessages()) {
            Message latestMessage = conversation.getLatestMessage();
            String sender = latestMessage.getDisplayName();
            String body = latestMessage.getBody();

            // ...

            /**
             * This method is safe and does not introduce vulnerabilities.
             */
            mBuilder.setContentTitle(sender);
            mBuilder.setContentText(body);

            // ...
        }

        return mBuilder;
    }

    // ...
}