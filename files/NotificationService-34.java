package eu.siacs.conversations.xmpp;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.NotificationManagerCompat;
import com.google.android.gms.common.images.ImageManager;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.qrcodescan.util.GeoHelper;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.ui.EditAccountActivity;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private static final int ERROR_NOTIFICATION_ID = 42; // ID for error notifications
    private static final int FOREGROUND_NOTIFICATION_ID = 100; // ID for foreground service notification

    private XmppConnectionService mXmppConnectionService; // Service instance holding the connection logic
    private Conversation mOpenConversation; // The currently open conversation in UI
    private boolean mIsInForeground = false; // Flag indicating if app is in foreground
    private long mLastNotification; // Timestamp of the last notification

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
        markLastNotification();
    }

    public static Pattern generateNickHighlightPattern(String nick) {
        if (nick == null || nick.trim().isEmpty()) {
            return Pattern.compile("(?!)"); // Match nothing
        } else {
            String[] parts = nick.split("\\s+");
            StringBuilder pattern = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    pattern.append("(^|\\W)")
                            .append(Pattern.quote(part))
                            .append("(?=\\W|$)|");
                }
            }
            // Remove trailing '|' from the string
            pattern.setLength(pattern.length() - 1);
            return Pattern.compile(pattern.toString(), Pattern.CASE_INSENSITIVE);
        }
    }

    public void pushNotification(String message) {
        if (message != null && !mIsInForeground) {
            NotificationCompat.Builder mBuilder = new Builder(mXmppConnectionService, "default");
            mBuilder.setContentTitle("New Message");
            mBuilder.setContentText(message);
            mBuilder.setSmallIcon(R.drawable.ic_notification_new_message);
            Intent intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(pendingIntent);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            // SECURITY CONCERN: This could potentially expose sensitive information if message content is untrusted
            notificationManager.notify(1, mBuilder.build());
        }
    }

    public void pushMessageNotification(Message message) {
        Conversation conversation = (Conversation) message.getConversation();
        Account account = conversation.getAccount();

        if (!mIsInForeground || !conversation.equals(mOpenConversation)) {
            String body = message.getBody();
            String name = conversation.getName();

            // SECURITY CONCERN: Ensure that 'body' and 'name' are sanitized to prevent potential security issues such as injection attacks
            Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "messages");
            mBuilder.setContentTitle(name);
            mBuilder.setContentText(body);

            PendingIntent contentIntent = createContentIntent(conversation);
            mBuilder.setContentIntent(contentIntent);
            mBuilder.setSmallIcon(R.drawable.ic_notification_new_message);
            mBuilder.setPriority(Notification.PRIORITY_HIGH);
            mBuilder.setAutoCancel(true);

            if (Compatibility.runsTwentySix()) {
                // SECURITY CONCERN: Ensure that notification channels are correctly configured to avoid security risks
                mBuilder.setChannelId("messages");
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            notificationManager.notify(name, conversation.getUuid().hashCode(), mBuilder.build());
        }
    }

    private PendingIntent createContentIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        intent.putExtra("conversation", conversation.getUuid());
        return PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void pushErrorNotification(Account account) {
        if (!mIsInForeground && account.hasErrorStatus()) {
            Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "errors");
            mBuilder.setContentTitle("Account Error");
            mBuilder.setContentText(account.getJid().asBareJid().toString());
            mBuilder.setSmallIcon(R.drawable.ic_notification_error);
            mBuilder.setPriority(Notification.PRIORITY_HIGH);
            mBuilder.setAutoCancel(true);

            PendingIntent contentIntent = createContentIntent(account);
            mBuilder.setContentIntent(contentIntent);

            if (Compatibility.runsTwentySix()) {
                // SECURITY CONCERN: Ensure that notification channels are correctly configured to avoid security risks
                mBuilder.setChannelId("errors");
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            notificationManager.notify(account.getJid().asBareJid().toString(), account.hashCode(), mBuilder.build());
        }
    }

    private PendingIntent createContentIntent(Account account) {
        Intent intent;
        if (AccountUtils.MANAGE_ACCOUNT_ACTIVITY != null) {
            intent = new Intent(mXmppConnectionService, AccountUtils.MANAGE_ACCOUNT_ACTIVITY);
        } else {
            intent = new Intent(mXmppConnectionService, EditAccountActivity.class);
            intent.putExtra("jid", account.getJid().asBareJid().toEscapedString());
            intent.putExtra(EditAccountActivity.EXTRA_OPENED_FROM_NOTIFICATION, true);
        }
        return PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void pushForegroundNotification() {
        if (!mIsInForeground) {
            Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "foreground");
            mBuilder.setContentTitle("Conversations Service Running");
            mBuilder.setContentText("Touch to open app");
            mBuilder.setSmallIcon(R.drawable.ic_notification_service);

            PendingIntent contentIntent = createOpenConversationsIntent();
            if (contentIntent != null) {
                mBuilder.setContentIntent(contentIntent);
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            // SECURITY CONCERN: Ensure that the foreground service notification is correctly managed to avoid issues
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, mBuilder.build());
        }
    }

    private PendingIntent createOpenConversationsIntent() {
        Intent intent;
        try {
            intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        } catch (RuntimeException e) {
            return null;
        }
        return PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    // Other methods and classes...

}