package com.example.xmpp; // Assuming this is within a package

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.RemoteInput;

import com.example.xmpp.entities.Account;
import com.example.xmpp.entities.Conversation;
import com.example.xmpp.entities.Message;
import com.example.xmpp.services.XmppConnectionService;
import com.example.xmpp.ui.ConversationActivity;
import com.example.xmpp.ui.ManageAccountActivity;
import com.example.xmpp.utils.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private static final int NOTIFICATION_ID_MULTIPLIER = 1000; // To avoid collisions between different actions
    public static final int FOREGROUND_NOTIFICATION_ID = 42;
    public static final int ERROR_NOTIFICATION_ID = 55;
    public static final String KEY_TEXT_REPLY = "key_text_reply";
    private final Context mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground = false;
    private long mLastNotification;

    public NotificationHelper(Context context) {
        this.mXmppConnectionService = context;
        markLastNotification();
    }

    public void pushMessageNotification(Message message, Account account) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        // Check if the app is in foreground or the conversation is open and skip notification
        if (mIsInForeground && mOpenConversation != null && mOpenConversation.getUuid().equals(message.getConversationUuid())) {
            return;
        }

        final Conversation conversation = message.getConversation();
        final String displayName = conversation.getName();

        // Check if the account is in a state where notifications should be suppressed
        if (account == null || !account.isOnlineAndConnected() || inMiniGracePeriod(account)) {
            return;
        }

        // Determine if the notification is for an important message, such as one highlighted by nickname or private message
        final boolean highlight = wasHighlightedOrPrivate(message);
        if (!highlight) {
            conversation.setLastMessageNotificationTime(System.currentTimeMillis());
        }

        // Create a base NotificationCompat.Builder instance
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

        // Set up the notification's appearance and behavior
        mBuilder.setContentTitle(displayName);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message.getBody()));
        mBuilder.setSmallIcon(R.drawable.ic_message_white_24dp);
        mBuilder.setColor(Color.GREEN); // Sets color for notifications

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Add reply action for Android N and above
            RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                    .setLabel(mXmppConnectionService.getString(R.string.reply))
                    .build();
            NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                    R.drawable.ic_reply_white_24dp,
                    mXmppConnectionService.getString(R.string.reply),
                    createReplyIntent(conversation, true))
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build();
            mBuilder.addAction(action);
        } else {
            // Add reply intent for older Android versions
            Intent replyIntent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent pendingReplyIntent = PendingIntent.getActivity(mXmppConnectionService,
                    generateRequestCode(conversation, 25),
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT);

            mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                    mXmppConnectionService.getString(R.string.reply),
                    pendingReplyIntent);
        }

        // Add mark as read action
        mBuilder.addAction(R.drawable.ic_done_white_24dp,
                mXmppConnectionService.getString(R.string.mark_as_read),
                createReadPendingIntent(conversation));

        // Set snooze action if the message is highlighted or private
        if (highlight) {
            mBuilder.addAction(R.drawable.ic_snooze_white_24dp,
                    mXmppConnectionService.getString(R.string.snooze),
                    createSnoozeIntent(conversation));
        }

        mBuilder.setContentIntent(createContentIntent(conversation));

        // Set the priority and visibility
        mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Show the notification
        notificationManager.notify(generateRequestCode(conversation, 1), mBuilder.build());
    }

    private boolean wasHighlightedOrPrivate(final Message message) {
        final String nick = message.getConversation().getMucOptions().getActualNick();
        final Pattern highlight = generateNickHighlightPattern(nick);
        if (message.getBody() == null || nick == null) {
            return false;
        }
        final Matcher m = highlight.matcher(message.getBody());
        // This pattern is safe to use as it's generated from the user's nickname, but ensure nickname validation
        return (m.find() || message.getType() == Message.TYPE_PRIVATE);
    }

    public static Pattern generateNickHighlightPattern(final String nick) {
        // Ensure proper handling of special characters in nicknames to avoid regex injection
        return Pattern.compile("\\b" + Pattern.quote(nick) + "\\p{Punct}?\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public void setOpenConversation(final Conversation conversation) {
        this.mOpenConversation = conversation;
    }

    public void setIsInForeground(final boolean foreground) {
        this.mIsInForeground = foreground;
    }

    private PendingIntent createReplyIntent(Conversation conversation, boolean dismissAfterReply) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_REPLY_TO_CONVERSATION);
        intent.putExtra("uuid",conversation.getUuid());
        intent.putExtra("dismiss_notification",dismissAfterReply);

        // Use unique request codes for different actions to avoid PendingIntent collision
        final int id =  generateRequestCode(conversation, dismissAfterReply ? 12 : 14);
        return PendingIntent.getService(mXmppConnectionService, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createReadPendingIntent(Conversation conversation) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_MARK_AS_READ);
        intent.putExtra("uuid", conversation.getUuid());
        intent.setPackage(mXmppConnectionService.getPackageName());

        // Use unique request codes for different actions to avoid PendingIntent collision
        return PendingIntent.getService(mXmppConnectionService, generateRequestCode(conversation,16), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public PendingIntent createSnoozeIntent(Conversation conversation) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_SNOOZE);
        intent.putExtra("uuid", conversation.getUuid());
        intent.setPackage(mXmppConnectionService.getPackageName());

        // Use unique request codes for different actions to avoid PendingIntent collision
        return PendingIntent.getService(mXmppConnectionService, generateRequestCode(conversation,22), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public Notification createForegroundNotification() {
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

        // Set notification content and actions when the service is in foreground
        mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.conversations_foreground_service));
        if (Config.SHOW_CONNECTED_ACCOUNTS) {
            List<Account> accounts = mXmppConnectionService.getAccounts();
            int enabled = 0;
            int connected = 0;
            for (Account account : accounts) {
                if (account.isOnlineAndConnected()) {
                    connected++;
                    enabled++;
                } else if (account.isEnabled()) {
                    enabled++;
                }
            }
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.connected_accounts, connected, enabled));
        } else {
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_open_conversations));
        }

        // Create an intent to open the main activity when notification is tapped
        mBuilder.setContentIntent(createOpenConversationsIntent());

        // Set timestamp and priority of the notification
        mBuilder.setWhen(0);
        mBuilder.setPriority(Config.SHOW_CONNECTED_ACCOUNTS ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN);

        // Set a small icon for the notification
        mBuilder.setSmallIcon(R.drawable.ic_link_white_24dp);

        // Build and return the notification object
        return mBuilder.build();
    }

    private PendingIntent createOpenConversationsIntent() {
        // Create an intent to start the main activity of the app
        return PendingIntent.getActivity(mXmppConnectionService, 0,
                new Intent(mXmppConnectionService, ConversationActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void updateErrorNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        // Collect accounts with errors
        List<Account> errors = new ArrayList<>();
        for (Account account : mXmppConnectionService.getAccounts()) {
            if (!account.isOnlineAndConnected()) {
                errors.add(account);
            }
        }

        // Check if there are no errors and cancel the error notification if it exists
        if (errors.isEmpty()) {
            notificationManager.cancel(ERROR_NOTIFICATION_ID);
            return;
        }

        // Create a notification for each account with an error
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService)
                .setContentTitle(mXmppConnectionService.getString(R.string.connection_issues))
                .setSmallIcon(R.drawable.ic_error_white_24dp);

        if (errors.size() == 1) {
            // If there's only one error, show details
            Account account = errors.get(0);
            mBuilder.setContentText(account.getName());
            Intent intent = new Intent(mXmppConnectionService, ManageAccountActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService,
                    generateRequestCode(account, 30),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(pendingIntent);
        } else {
            // If there are multiple errors, show a summary notification
            NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
            for (Account account : errors) {
                inboxStyle.addLine(account.getName());
            }
            mBuilder.setStyle(inboxStyle);
            Intent intent = new Intent(mXmppConnectionService, ManageAccountActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService,
                    generateRequestCode(null, 30),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(pendingIntent);
        }

        // Set priority and visibility of the notification
        mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Show the error notification
        notificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }
}