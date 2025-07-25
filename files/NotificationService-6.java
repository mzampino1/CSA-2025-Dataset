import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.util.DisplayMetrics;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {

    // Potential Vulnerability: If this service is exposed to external applications, it might be vulnerable to unauthorized access.
    private final Context mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;
    private static final int NOTIFICATION_ID = 123; // Unique notification ID
    private static final int ERROR_NOTIFICATION_ID = 456; // Error notification ID

    public NotificationService(Context context) {
        this.mXmppConnectionService = context;
    }

    // Method to check if a new message should trigger a notification.
    public boolean shouldNotify(Message message, Conversation conversation) {
        return (message.getStatus() == Message.Status.RECEIVED && !conversation.isMuted());
    }

    // Potential Vulnerability: If the message body is not sanitized before being used in notifications,
    // it could lead to injection attacks if an attacker can control the content of the messages.
    public void notifyMessage(Message message, Conversation conversation) {
        if (!shouldNotify(message, conversation)) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(conversation.getName())
                .setContentText(message.getBody()) // Potential injection point
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
        intent.putExtra("conversation_id", conversation.getId());
        PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    // Method to handle clearing notifications.
    public void clearNotifications() {
        NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    // Potential Vulnerability: If error messages are not properly sanitized before being displayed,
    // it could lead to injection attacks. This is especially true if the error messages can be
    // influenced by external factors.
    public void showErrorNotification(List<Account> accounts) {
        if (accounts.isEmpty()) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setSmallIcon(R.drawable.ic_error);

        if (accounts.size() == 1) {
            builder.setContentTitle("Error with account: " + accounts.get(0).getJid());
            // Potential injection point
        } else {
            builder.setContentTitle("Errors with multiple accounts");
        }

        Intent intent = new Intent(mXmppConnectionService, AccountSettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(ERROR_NOTIFICATION_ID, builder.build());
    }

    // Method to update error notifications.
    public void updateErrorNotifications() {
        List<Account> accountsWithError = new ArrayList<>();
        for (Account account : getAccounts()) { // Assume getAccounts() returns a list of accounts
            if (account.hasError()) {
                accountsWithError.add(account);
            }
        }
        showErrorNotification(accountsWithError);
    }

    // Placeholder method to retrieve accounts.
    private List<Account> getAccounts() {
        return new ArrayList<>();
    }
}