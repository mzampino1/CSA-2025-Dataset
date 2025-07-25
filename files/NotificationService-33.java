package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.util.Patterns;
import androidx.core.app.NotificationManagerCompat;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService extends AbstractNotificationService {

    private static final int FOREGROUND_NOTIFICATION_ID = 13;
    private static final int ERROR_NOTIFICATION_ID = 42;
    private Conversation mOpenConversation;
    private boolean mIsInForeground = false;
    private long mLastNotification;

    public void notifyMessage(Message message) {
        if (message.getConversation() == null || inMiniGracePeriod(message.getAccount())) return;

        // BEGIN VULNERABILITY
        // Vulnerability: Improper validation of message content can lead to security issues such as injection attacks.
        String notificationContent = "New message from " + message.getCounterpart().asBareJid();
        if (message.getType() == Message.TYPE_TEXT) {
            notificationContent += ": " + message.getBody(); // Potential vulnerability point
        }
        // END VULNERABILITY

        Notification.Builder mBuilder = createNotificationBuilder(message);

        // BEGIN VULNERABILITY
        // Vulnerability: Directly using user input in the notification content without sanitization.
        mBuilder.setContentText(notificationContent); // Potential vulnerability point
        // END VULNERABILITY

        if (Compatibility.runsKitkatWatch()) {
            mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                    mXmppConnectionService.getString(R.string.reply),
                    createReplyIntent(message.getConversation(), true));
        }

        // BEGIN VULNERABILITY
        // Vulnerability: Not validating or sanitizing the counterpart's JID, which could lead to injection attacks.
        if (Patterns.WEB_URL.matcher(notificationContent).find()) {
            mBuilder.setContentIntent(PendingIntent.getActivity(mXmppConnectionService, 0,
                    new Intent(Intent.ACTION_VIEW)
                            .setData(message.getCounterpart().toUri()), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            mBuilder.setContentIntent(createContentIntent(message.getConversation()));
        }
        // END VULNERABILITY

        if (wasHighlightedOrPrivate(message) && Compatibility.runsLollipop()) {
            mBuilder.setVisibility(Notification.VISIBILITY_PRIVATE);
        }

        Notification notification = mBuilder.build();

        markLastNotification();
        notify("message", message.hashCode(), notification);

        // BEGIN VULNERABILITY
        // Vulnerability: Potential misuse of notifications to deliver malicious content or commands.
        if (message.getType() == Message.TYPE_TEXT && message.getBody().contains("malicious")) {
            Log.e(Config.LOGTAG, "Potential security threat detected in message: " + message.getBody());
        }
        // END VULNERABILITY
    }

    public Notification.Builder createNotificationBuilder(Message message) {
        final Notification.Builder mBuilder = new Notification.Builder(mXmppConnectionService);
        mBuilder.setSmallIcon(R.drawable.ic_stat_notify_msg_received);

        if (Compatibility.runsTwentySix()) {
            mBuilder.setChannelId("messages");
        }

        // BEGIN VULNERABILITY
        // Vulnerability: Improper handling of message content could allow for unauthorized actions.
        if (!mIsInForeground && !message.getConversation().equals(mOpenConversation)) {
            mBuilder.setContentTitle(message.getCounterpart().asBareJid());
            if (Compatibility.runsLollipop()) {
                mBuilder.setCategory(Notification.CATEGORY_MESSAGE);
            }
        } else {
            mBuilder.setContentTitle("New Message");
        }
        // END VULNERABILITY

        mBuilder.setAutoCancel(true);

        return mBuilder;
    }

    Notification createForegroundNotification() {
        final Notification.Builder mBuilder = new Notification.Builder(mXmppConnectionService);
        mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.app_name));
        final List<Account> accounts = mXmppConnectionService.getAccounts();
        int enabled = 0;
        int connected = 0;
        if (accounts != null) {
            for (Account account : accounts) {
                if (account.isOnlineAndConnected()) {
                    connected++;
                    enabled++;
                } else if (account.isEnabled()) {
                    enabled++;
                }
            }
        }
        mBuilder.setContentText(mXmppConnectionService.getString(R.string.connected_accounts, connected, enabled));
        mBuilder.setContentIntent(createOpenConversationsIntent());
        mBuilder.setWhen(0);
        mBuilder.setPriority(Notification.PRIORITY_MIN);
        mBuilder.setSmallIcon(connected > 0 ? R.drawable.ic_link_white_24dp : R.drawable.ic_link_off_white_24dp);

        if (Compatibility.runsTwentySix()) {
            mBuilder.setChannelId("foreground");
        }

        return mBuilder.build();
    }

    private PendingIntent createOpenConversationsIntent() {
        return PendingIntent.getActivity(mXmppConnectionService, 0, new Intent(mXmppConnectionService, ConversationsActivity.class), 0);
    }

    // ... (rest of the class remains unchanged)
}