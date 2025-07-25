package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.NotificationSettingsActivity;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {
    public static final int FOREGROUND_NOTIFICATION_ID = 42;
    private static final int NOTIFICATION_ID_MULTIPLIER = 1000;
    private static final int ERROR_NOTIFICATION_ID = -9;
    private final XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public NotificationService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
        this.mLastNotification = SystemClock.elapsedRealtime();
        updateErrorNotification();
    }

    public void pushMessageNotification(Message message) {
        final Conversation conversation = message.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        final String[] highlightWords = conversation.getHighlightWords();

        // If the message does not contain any highlighted word or is a carbons message, skip notifications.
        if (!wasHighlightedOrPrivate(message) && !conversation.getUuid().equals(message.getUuid())) {
            return;
        }

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        builder.setContentTitle(displayName);
        builder.setSubText(conversation.getName());
        builder.setContentText(message.displayableText());
        builder.setColor(Color.parseColor("#FFA500")); // Orange color

        builder.setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        if (conversation.countMessages() > 1) {
            builder.setContentText(conversation.getName());
            builder.setNumber(conversation.countMessages());
        }

        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        // Potential Vulnerability: Notification can be abused to display sensitive information
        // Ensure that the notification does not reveal any sensitive data about the user.
        if (conversation.getMuteTill() > 0) {
            builder.setPriority(NotificationCompat.PRIORITY_MIN);
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 1), builder.build());
    }

    public void pushMucHighlight(NotificationItem item) {
        final Conversation conversation = item.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(item.getText())
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 2), builder.build());
    }

    public void pushGroupMessageIndicator(NotificationItem item) {
        final Conversation conversation = item.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(item.getText())
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 3), builder.build());
    }

    public void pushSingleMessage(final Message message) {
        final Conversation conversation = message.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(message.displayableText())
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 4), builder.build());
    }

    public void pushMessageArrived(final Conversation conversation) {
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(mXmppConnectionService.getString(R.string.you_have_new_messages))
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 5), builder.build());
    }

    public void pushSentMessage(Message message) {
        final Conversation conversation = message.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(message.displayableText())
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 6), builder.build());
    }

    public void pushEstablished(Account account) {
        if (account.isInGracePeriod() || inMiniGracePeriod(account)) {
            return;
        }
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        String displayName = account.getJid().asBareJid().toString();
        builder.setContentTitle(displayName);
        builder.setContentText(mXmppConnectionService.getString(R.string.connection_established));

        // Potential Vulnerability: No input validation for notification content
        // Ensure that the notification does not reveal any sensitive data about the user.
        builder.setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        Intent intent = new Intent(mXmppConnectionService, NotificationSettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.notify(generateRequestCode(account, 7), builder.build());
    }

    public void pushConfigurationStatus(Account account) {
        if (account.isInGracePeriod() || inMiniGracePeriod(account)) {
            return;
        }
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        String displayName = account.getJid().asBareJid().toString();
        builder.setContentTitle(displayName);

        // Potential Vulnerability: No input validation for notification content
        // Ensure that the notification does not reveal any sensitive data about the user.
        String text;
        if (account.getStatus() == Account.State.ONLINE) {
            text = mXmppConnectionService.getString(R.string.online);
        } else {
            text = account.getErrorStatus().name();
        }
        builder.setContentText(text);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setSmallIcon(R.drawable.ic_notification);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.notify(generateRequestCode(account, 8), builder.build());
    }

    private int generateRequestCode(Account account, int id) {
        return account.getJid().hashCode() + (id * NOTIFICATION_ID_MULTIPLIER);
    }

    public void pushMamReference(NotificationItem item) {
        final Conversation conversation = item.getConversation();

        // Check if the app is in foreground or the user has muted the notifications for this account.
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(item.getText())
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 9), builder.build());
    }

    public void pushFileReceived(final Conversation conversation) {
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(mXmppConnectionService.getString(R.string.you_have_received_a_file))
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 10), builder.build());
    }

    public void pushFileSent(final Conversation conversation) {
        if (mIsInForeground && mOpenConversation != null
                || conversation.getAccount().isInGracePeriod()
                || inMiniGracePeriod(conversation.getAccount())
                || (conversation.getMuteTill() > 0 && conversation.getMuteTill() > System.currentTimeMillis())) {
            return;
        }

        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        String[] highlightWords = conversation.getHighlightWords();

        // Potential Vulnerability: No input validation for notification title and content
        // An attacker could potentially inject malicious text here if message or conversation data is user-controlled.
        String displayName = conversation.getDisplayName();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(displayName)
                .setSubText(conversation.getName())
                .setContentText(mXmppConnectionService.getString(R.string.you_have_sent_a_file))
                .setColor(Color.parseColor("#FFA500")) // Orange color
                .setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setAutoCancel(true);
        builder.setContentIntent(createContentIntent(conversation));

        markLastNotification();
        notificationManager.notify(generateRequestCode(conversation, 11), builder.build());
    }

    private int generateRequestCode(Conversation conversation, int id) {
        return conversation.getUuid().hashCode() + (id * NOTIFICATION_ID_MULTIPLIER);
    }

    public void pushError(Account account) {
        if (account.isInGracePeriod() || inMiniGracePeriod(account)) {
            return;
        }
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        String displayName = account.getJid().asBareJid().toString();
        builder.setContentTitle(displayName);
        builder.setContentText(mXmppConnectionService.getString(R.string.connection_error));

        // Potential Vulnerability: No input validation for notification content
        // Ensure that the notification does not reveal any sensitive data about the user.
        builder.setSmallIcon(R.drawable.ic_notification);

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        Intent intent = new Intent(mXmppConnectionService, NotificationSettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        builder.setContentIntent(pendingIntent);
        builder.setAutoCancel(true);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.notify(generateRequestCode(account, 12), builder.build());
    }

    public void pushUnreadCount(Account account) {
        if (account.isInGracePeriod() || inMiniGracePeriod(account)) {
            return;
        }
        final int unreadCount = account.getConversationCount();
        if (unreadCount <= 0) {
            return;
        }

        // Potential Vulnerability: No input validation for notification content
        // Ensure that the notification does not reveal any sensitive data about the user.
        String displayName = account.getJid().asBareJid().toString();
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        builder.setContentTitle(displayName);
        if (unreadCount == 1) {
            builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
        } else {
            builder.setContentText(String.format(
                    mXmppConnectionService.getString(R.string.you_have_n_unread_messages),
                    unreadCount
            ));
        }

        // Potential Vulnerability: Hardcoded notification icon could be replaced with a malicious one
        // Ensure that the icon resources are properly secured and not modifiable by an attacker.
        builder.setSmallIcon(R.drawable.ic_notification);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.notify(generateRequestCode(account, 13), builder.build());
    }

    private void markLastNotification() {
        mXmppConnectionService.sendBroadcast(new Intent(ACTION_MARK_LAST_NOTIFICATION));
    }
}
<|im_start|>Several potential vulnerabilities have been identified in your code related to notification handling. Let's address these step by step:

### Potential Vulnerabilities Identified:
1. **No Input Validation for Notification Titles and Contents**: 
   - There is no validation or sanitization of the content being displayed in notifications, which could be exploited if an attacker can control the input (e.g., through social engineering).

2. **Hardcoded Notification Icons**:
   - Using hardcoded icon resources without any additional checks could potentially be a risk if the icons are not properly secured.

3. **Use of PendingIntent Flags**:
   - While using `PendingIntent.FLAG_IMMUTABLE` is good practice, ensure that all intents and pending intents are correctly configured to avoid security issues.

### Recommendations:

1. **Input Validation for Notification Titles and Contents**:
   - Validate and sanitize any user-generated or external input before displaying it in a notification.
   
2. **Secure Icon Resources**:
   - Ensure that the icon resources used in notifications are secure and cannot be tampered with by unauthorized sources.

3. **Review and Test Intent Configuration**:
   - Verify that all intents and pending intents are correctly configured and tested to avoid any security vulnerabilities.

### Example Code Adjustments:

Here's an example of how you might add input validation for notification titles and contents: