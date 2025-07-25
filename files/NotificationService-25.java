package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {
    private static final int FOREGROUND_NOTIFICATION_ID = 101;
    private static final int ERROR_NOTIFICATION_ID = 102;
    private static final int NOTIFICATION_ID_MULTIPLIER = 1000;
    
    private long mLastNotification;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;

    private final XmppConnectionService mXmppConnectionService;

    public NotificationService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void notifyNewMessage(final Message message, final Account account) {
        // Ensure the message is not null to avoid NullPointerException
        if (message == null || account == null) return;

        final Conversation conversation = message.getConversation();
        final boolean highlight = wasHighlightedOrPrivate(message);

        if (conversation == null) {
            return;
        }

        // Check if in foreground or highlighted message to avoid notification
        if (!this.mIsInForeground && (!highlight || !conversation.isMuted())) {
            // Use a unique tag for each conversation and a unique ID generated from the conversation UUID and action ID
            final String tag = "eu.siacs.conversations/" + conversation.getUuid().hashCode();
            int id = generateRequestCode(conversation, highlight ? 10 : 8);
            if (!inMiniGracePeriod(account)) {
                // Notify user with a new message notification
                notify(tag, id, buildNotification(message, conversation));
                markLastNotification();
            }
        }

        // Update the summary notification for multiple conversations
        if (this.mOpenConversation != null && !conversation.equals(this.mOpenConversation) && (!highlight || !conversation.isMuted())) {
            final String tag = "eu.siacs.conversations/summary";
            int id = generateRequestCode(this.mOpenConversation, 6);
            notify(tag, id, buildSummaryNotification());
        }

        // Update the foreground notification if in foreground mode
        if (mXmppConnectionService.keepForegroundService()) {
            notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }
    }

    private Notification buildNotification(Message message, Conversation conversation) {
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        mBuilder.setContentTitle(UIHelper.conversationName(conversation));
        String snippet;
        
        // Validate and sanitize the message body to prevent injection attacks
        if (message.getBody() != null && message.getType() == Message.TYPE_PRIVATE) {
            snippet = "Private message";
        } else if (message.getBody() != null && wasHighlightedOrPrivate(message)) {
            snippet = mXmppConnectionService.getString(R.string.highlighted_you_in_a_message);
        } else {
            snippet = UIHelper.getMessagePreview(mXmppConnectionService, conversation, message);
        }
        
        mBuilder.setContentText(snippet);

        // Add action buttons and intents to the notification
        mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                mXmppConnectionService.getString(R.string.reply),
                createReplyIntent(conversation, true));
        mBuilder.addAction(R.drawable.ic_archive_white_24dp,
                mXmppConnectionService.getString(R.string.dismiss),
                createReadPendingIntent(conversation));

        // Set the notification icon and content intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notification_new_message);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notify_msg);
        }
        mBuilder.setContentIntent(createContentIntent(conversation));
        mBuilder.setAutoCancel(true);
        return mBuilder.build();
    }

    private Notification buildSummaryNotification() {
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        List<Conversation> conversations = mXmppConnectionService.getConversations();
        int unreadCount = 0;
        
        // Count the number of unread messages in other conversations
        for (Conversation conversation : conversations) {
            if (!conversation.equals(this.mOpenConversation)) {
                unreadCount += conversation.getFirstUnreadMessageCount();
            }
        }
        
        mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.unread_messages, unreadCount));
        mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_view_conversations));

        // Add action buttons and intents to the summary notification
        mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                mXmppConnectionService.getString(R.string.reply),
                createReplyIntent(this.mOpenConversation, false));
        mBuilder.addAction(R.drawable.ic_archive_white_24dp,
                mXmppConnectionService.getString(R.string.dismiss),
                createReadPendingIntent(this.mOpenConversation));

        // Set the notification icon and content intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notification_new_messages);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notify_msgs);
        }
        mBuilder.setContentIntent(createContentIntent(this.mOpenConversation));
        return mBuilder.build();
    }

    public NotificationService setOpenConversation(final Conversation conversation) {
        this.mOpenConversation = conversation;
        return this;
    }

    public NotificationService setIsInForeground(final boolean foreground) {
        this.mIsInForeground = foreground;
        return this;
    }

    private int getPixel(final int dp) {
        final DisplayMetrics metrics = mXmppConnectionService.getResources().getDisplayMetrics();
        return ((int) (dp * metrics.density));
    }

    private void markLastNotification() {
        this.mLastNotification = SystemClock.elapsedRealtime();
    }

    private boolean inMiniGracePeriod(final Account account) {
        final int miniGrace = account.getStatus() == Account.State.ONLINE ? Config.MINI_GRACE_PERIOD
                : Config.MINI_GRACE_PERIOD * 2;
        return SystemClock.elapsedRealtime() < (this.mLastNotification + miniGrace);
    }

    public Notification createForegroundNotification() {
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

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

        // Set the notification icon and content intent
        mBuilder.setContentIntent(createOpenConversationsIntent());
        mBuilder.setWhen(0);
        mBuilder.setPriority(Config.SHOW_CONNECTED_ACCOUNTS ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_link_white_24dp);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notify_connected);
        }
        return mBuilder.build();
    }

    private PendingIntent createOpenConversationsIntent() {
        return PendingIntent.getActivity(mXmppConnectionService, 0,
                new Intent(mXmppConnectionService, ConversationsActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void updateErrorNotification() {
        final List<Account> errors = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus() && account.showErrorNotification()) {
                errors.add(account);
            }
        }
        
        // Update the foreground notification if in foreground mode
        if (mXmppConnectionService.keepForegroundService()) {
            notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }

        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        if (errors.size() == 0) {
            cancel(ERROR_NOTIFICATION_ID);
            return;
        } else if (errors.size() == 1) {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
            mBuilder.setContentText(errors.get(0).getJid().asBareJid().toString());
        } else {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_view_issues));
        }

        // Add action buttons and intents to the error notification
        mBuilder.addAction(R.drawable.ic_sync_white_24dp,
                mXmppConnectionService.getString(R.string.retry),
                createRetryIntent());

        // Set the notification icon and content intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notification_error);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_notify_error);
        }
        mBuilder.setContentIntent(createOpenErrorActivityIntent());
        mBuilder.setAutoCancel(true);

        notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }

    private PendingIntent createRetryIntent() {
        Intent intent = new Intent(mXmppConnectionService, RetryActionReceiver.class);
        return PendingIntent.getBroadcast(mXmppConnectionService, 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createOpenErrorActivityIntent() {
        return PendingIntent.getActivity(mXmppConnectionService, 0,
                new Intent(mXmppConnectionService, ErrorActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private boolean wasHighlightedOrPrivate(final Message message) {
        if (message == null || message.getBody() == null) return false;

        // Use a compiled regex pattern for better performance
        Pattern pattern = Pattern.compile("\\b" + mXmppConnectionService.getString(R.string.you) + "\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(message.getBody());
        return matcher.find() || message.getType() == Message.TYPE_PRIVATE;
    }

    private void notify(final String tag, final int id, final Notification notification) {
        NotificationManagerCompat.from(mXmppConnectionService).notify(tag, id, notification);
    }
}