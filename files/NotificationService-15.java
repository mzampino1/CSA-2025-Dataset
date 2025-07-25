package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.utils.GeoHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {

    private final XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation = null;
    private boolean mIsInForeground = false;
    private long mLastNotification = 0;
    public static final int FOREGROUND_NOTIFICATION_ID = 42; // ID for foreground notification
    public static final int ERROR_NOTIFICATION_ID = 18; // ID for error notifications

    public NotificationService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void clear() {
        // Clear all notifications
        final NotificationManagerCompat nm = NotificationManagerCompat.from(mXmppConnectionService);
        nm.cancelAll();
    }

    private boolean isNotificationEnabledForConversation(final Conversation conversation) {
        if (conversation == null) {
            return false; // No conversation, no notification
        }
        if (conversation.isRead()) {
            return false; // If conversation is read, no notification needed
        }
        final Account account = conversation.getAccount();
        if (!account.isLoggedIn() || !account.isOnlineAndConnected()) {
            return false; // Don't notify for conversations on accounts that are not logged in or connected
        }
        return true;
    }

    private void showForegroundNotification(final Conversation conversation) {
        // Show foreground notification indicating service is running
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        if (conversation == null || !mIsInForeground) {
            // No open conversation or not in foreground, show generic foreground service notification
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.conversations_foreground_service));
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_open_conversations));
        } else {
            // Open conversation is available, show notification with conversation details
            mBuilder.setContentTitle(conversation.getName());
            if (conversation.getLatestMessage() != null) {
                final Message message = conversation.getLatestMessage();
                mBuilder.setContentText(message.getType() == Message.TYPE_IMAGE ? mXmppConnectionService.getString(R.string.new_image_from_x, message.getContact().getDisplayName()) : message.getBody());
            }
        }

        mBuilder.setWhen(0); // No timestamp
        mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        mBuilder.setSmallIcon(R.drawable.ic_link_white_24dp);
        if (Config.SHOW_DISABLE_FOREGROUND) {
            final int cancelIcon;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mBuilder.setCategory(Notification.CATEGORY_SERVICE); // Set notification category for Lollipop+
                cancelIcon = R.drawable.ic_cancel_white_24dp; // Icon to use for disabling foreground service
            } else {
                cancelIcon = R.drawable.ic_action_cancel; // Fallback icon for older versions
            }
            mBuilder.addAction(cancelIcon,
                    mXmppConnectionService.getString(R.string.disable_foreground_service), // Action label
                    createDisableForeground()); // PendingIntent to disable foreground service
        }

        if (!mIsInForeground) {
            // If not in foreground, show generic foreground notification
            mBuilder.setContentIntent(createOpenConversationsIntent());
        } else {
            // Otherwise, show notification for the open conversation
            mBuilder.setContentIntent(createContentIntent(conversation));
        }
        final Notification notification = mBuilder.build();
        this.mXmppConnectionService.startForeground(FOREGROUND_NOTIFICATION_ID, notification);
    }

    private void updateNotifications() {
        if (mIsInForeground) {
            // If service is in foreground, show open conversation notification
            Conversation conversation = mOpenConversation;
            if (conversation != null && !isNotificationEnabledForConversation(conversation)) {
                conversation = this.mXmppConnectionService.findFirstUnread();
            }
            showForegroundNotification(conversation);
        } else {
            // Otherwise, clear foreground notification
            this.mXmppConnectionService.stopForeground(false);
        }

        final DatabaseBackend db = new DatabaseBackend(mXmppConnectionService);
        final List<Conversation> notifications = db.getConversationsWithNotifications();

        for (final Conversation conversation : notifications) {
            if (!isNotificationEnabledForConversation(conversation)) {
                // Skip notification for this conversation
                continue;
            }

            final Account account = conversation.getAccount();
            if (account == null) {
                continue; // No account, skip
            }
            if (account.countMessageUuids() > Config.MAX_NOTIFICATIONS_PER_CONVERSATION) {
                // Too many messages, clear old notifications and show summary
                db.clearMessageUuids(account);
            }

            final int notificationId = conversation.hashCode();
            final Message message = conversation.getLatestMessage();

            if (message == null || message.getType() == Message.TYPE_PRIVATE && !conversation.getMucOptions().online()) {
                continue; // No latest message or offline group chat with private message, skip
            }
            db.addMessageUuid(account, notificationId);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
            if (account.countMessageUuids() > 1) {
                // Multiple messages for this account, show summary
                mBuilder.setContentTitle(account.getJid().asBareJid().toString());
                int messageCount = account.countMessageUuids();
                mBuilder.setSubText(mXmppConnectionService.getResources().getQuantityString(R.plurals.number_of_unread_conversations, messageCount, messageCount));
            } else {
                // Single message, show notification with details
                mBuilder.setContentTitle(conversation.getName());
                if (message.getType() == Message.TYPE_IMAGE) {
                    mBuilder.setContentText(mXmppConnectionService.getString(R.string.new_image_from_x, message.getContact().getDisplayName()));
                } else {
                    String body = message.getBody();
                    if (conversation.getMode() == Conversation.MODE_MULTI && !conversation.getMucOptions().isPrivateMessageFromMuc(message)) {
                        // For group chats, prepend sender name to message
                        body = mXmppConnectionService.getString(R.string.message_from_x_y, message.getTrueCounterpart().getResourcepart(), body);
                    }
                    mBuilder.setContentText(body);
                }

                if (wasHighlightedOrPrivate(message) && !inMiniGracePeriod(account)) {
                    // Message was highlighted or is a private message and not in grace period
                    int accentColor = 0xff3f51b5;
                    account.getAxolotlService().refreshServiceConnection();
                    AxolotlService axolotlService = account.getAxolotlService();
                    if (axolotlService != null) {
                        accentColor = axolotlService.getMessageNotificationColor();
                    }
                    mBuilder.setColor(accentColor);
                }

                // Add action to open conversation
                mBuilder.setContentIntent(createContentIntent(conversation));
            }

            mBuilder.setSmallIcon(R.drawable.ic_stat_message);
            if (message.getType() == Message.TYPE_IMAGE) {
                mBuilder.setStyle(new NotificationCompat.BigPictureStyle().bigPicture(((DatabaseBackend) message).getFile()));
            } else {
                mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(message.getBody()));
            }

            // Add delete action to clear notification
            Intent dismissIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
            dismissIntent.setAction(XmppConnectionService.ACTION_DISMISS_NOTIFICATION);
            dismissIntent.putExtra("uuid", conversation.getUuid());
            PendingIntent deletePendingIntent = PendingIntent.getService(
                    mXmppConnectionService,
                    conversation.hashCode() % 293785,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

            mBuilder.setDeleteIntent(deletePendingIntent);

            if (message.getType() == Message.TYPE_LOCATION && GeoHelper.isLocationMessage(message)) {
                // Add action to show location for location messages
                mBuilder.addAction(R.drawable.ic_map_white_24dp, "Show Location", createShowLocationIntent(message));
            }

            // Show notification
            NotificationManagerCompat.from(mXmppConnectionService).notify(notificationId, mBuilder.build());

            markLastNotification();
        }
    }

    public void update() {
        if (mIsInForeground) {
            showForegroundNotification(this.mOpenConversation);
        } else {
            updateNotifications();
        }
    }

    private void setLastNotification() {
        this.mLastNotification = SystemClock.elapsedRealtime();
    }

    private boolean inMiniGracePeriod(Account account) {
        int miniGrace = account.getStatus() == Account.State.ONLINE ? Config.MINI_GRACE_PERIOD : Config.MINI_GRACE_PERIOD_OFFLINE;
        return SystemClock.elapsedRealtime() - this.mLastNotification < miniGrace;
    }

    public void clear(final Conversation conversation) {
        // Clear notifications for a specific conversation
        NotificationManagerCompat nm = NotificationManagerCompat.from(mXmppConnectionService);
        nm.cancel(conversation.hashCode());
        DatabaseBackend db = new DatabaseBackend(mXmppConnectionService);
        Account account = conversation.getAccount();
        if (account != null) {
            db.removeMessageUuid(account, conversation.hashCode());
        }
    }

    private boolean wasHighlightedOrPrivate(Message message) {
        return message.isGeocoded() || message.getType() == Message.TYPE_PRIVATE;
    }

    public void clear(final String uuid) {
        // Clear notifications for a specific UUID
        NotificationManagerCompat nm = NotificationManagerCompat.from(mXmppConnectionService);
        Conversation conversation = this.mXmppConnectionService.findConversationByUuid(uuid);
        if (conversation != null) {
            nm.cancel(conversation.hashCode());
            Account account = conversation.getAccount();
            DatabaseBackend db = new DatabaseBackend(mXmppConnectionService);
            if (account != null) {
                db.removeMessageUuid(account, conversation.hashCode());
            }
        }
    }

    public void clear() {
        // Clear all notifications
        NotificationManagerCompat nm = NotificationManagerCompat.from(mXmppConnectionService);
        nm.cancelAll();
    }

    private PendingIntent createDisableForeground() {
        Intent disableIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        disableIntent.setAction(XmppConnectionService.ACTION_DISABLE_FOREGROUND_SERVICE);
        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                disableIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createOpenConversationsIntent() {
        Intent openIntent = new Intent(mXmppConnectionService, ConversationActivity.class);
        openIntent.setAction(Intent.ACTION_MAIN);
        openIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        return PendingIntent.getActivity(
                mXmppConnectionService,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createContentIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
        intent.putExtra("conversation", conversation.getUuid());
        return PendingIntent.getActivity(
                mXmppConnectionService,
                conversation.hashCode() % 923785,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createShowLocationIntent(Message message) {
        Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
        intent.putExtra("conversation", message.getConversation().getUuid());
        intent.putExtra("location_message_uid", message.getUid());
        return PendingIntent.getActivity(
                mXmppConnectionService,
                (message.getConversation().hashCode() * 13 + message.hashCode()) % 293785,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }
}