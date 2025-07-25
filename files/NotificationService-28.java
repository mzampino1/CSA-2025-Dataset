package eu.siacs.conversations.utils;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;
import com.google.zxing.common.detector.MathUtils;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.xmpp.XmppConnectionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private static final int NOTIFICATION_ID = 13425;
    private static final int ERROR_NOTIFICATION_ID = 83927;
    private static final int FOREGROUND_NOTIFICATION_ID = 6542;
    private XmppConnectionService mXmppConnectionService;
    private boolean mIsInForeground;
    private Conversation mOpenConversation;
    private long mLastNotification;

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void clear() {
        ((NotificationManager)mXmppConnectionService.getSystemService(mXmppConnectionService.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        cancel(ERROR_NOTIFICATION_ID);
    }

    private Pattern generateNickHighlightPattern(final String nick) {
        if (nick == null || nick.equals("")) {
            return Pattern.compile("(?!)");
        }
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nick.length(); ++i) {
            char c = nick.charAt(i);
            switch(c) {
                case '[':
                    builder.append("\\[");
                    break;
                case ']':
                    builder.append("\\]");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '(':
                    builder.append("\\(");
                    break;
                case ')':
                    builder.append("\\)");
                    break;
                default:
                    builder.append(c);
            }
        }
        return Pattern.compile("(^|\\W)(" + builder.toString() + ")(?=($|\\b))",Pattern.CASE_INSENSITIVE);
    }

    public void update() {
        if (mIsInForeground) {
            return;
        }
        final List<Message> messages = mXmppConnectionService.getPendingMessages();
        int unreadCount = 0;
        for (Message message : messages) {
            if (!message.isRead()) {
                ++unreadCount;
            }
        }

        if (unreadCount == 0 && !mXmppConnectionService.hasFailedConversations()) {
            clear();
        } else {
            final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "chat");
            mBuilder.setSmallIcon(R.drawable.ic_notification);
            mBuilder.setContentIntent(createOpenConversationsIntent());
            mBuilder.setAutoCancel(true);
            if (unreadCount == 0) {
                mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.connection_established));
                mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_open_conversations));
            } else if (unreadCount == 1) {
                Message message = messages.get(0);
                String sender = message.getMergedContactDisplayName();
                String body = message.getType() == Message.TYPE_FILE_TRANSFER ? mXmppConnectionService.getString(R.string.file_transferred, sender)
                        : Html.fromHtml(message.getBody().replaceAll("<img.+?>", "")); // Potential vulnerability: Improper handling of message body
                mBuilder.setContentTitle(sender);
                mBuilder.setContentText(body);
            } else {
                String unreadConversations = mXmppConnectionService.getString(R.string.unread_conversations, unreadCount);
                if (mOpenConversation != null && mXmppConnectionService.isOneToOne(mOpenConversation)) {
                    final int count = mXmppConnectionService.getPingCount();
                    final String messageSummary;
                    if (count == 0) {
                        messageSummary = unreadConversations;
                    } else {
                        messageSummary = mXmppConnectionService.getRes().getQuantityString(R.plurals.number_of_participants, count, count) + " · " + unreadConversations;
                    }
                    mBuilder.setContentTitle(mOpenConversation.getMucOptions().getActualNick());
                    mBuilder.setContentText(messageSummary);
                } else {
                    mBuilder.setContentTitle(unreadConversations);
                    if (messages.size() > 0) {
                        final NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
                        for (Message message : messages) {
                            String sender = message.getMergedContactDisplayName();
                            String body = message.getType() == Message.TYPE_FILE_TRANSFER ? mXmppConnectionService.getString(R.string.file_transferred, sender)
                                    : Html.fromHtml(message.getBody().replaceAll("<img.+?>", "")); // Potential vulnerability: Improper handling of message body
                            inboxStyle.addLine(sender + ": " + body);
                        }
                        mBuilder.setStyle(inboxStyle);
                    }
                }
            }

            if (unreadCount > 0) {
                String summary = mXmppConnectionService.getRes().getQuantityString(R.plurals.number_of_unread_messages, unreadCount, unreadCount);
                if (!inMiniGracePeriod(mOpenConversation.getAccount())) {
                    mBuilder.setNumber(unreadCount);
                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
                    mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                            mXmppConnectionService.getString(R.string.reply),
                            createReplyIntent(mOpenConversation, false));
                    if (!mIsInForeground) {
                        markLastNotification();
                    }
                } else {
                    summary = "";
                    mBuilder.setPriority(NotificationCompat.PRIORITY_LOW);
                }

                mBuilder.setContentTitle(summary.isEmpty() ? mXmppConnectionService.getString(R.string.conversations)
                        : summary);
                mBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            } else if (mXmppConnectionService.hasFailedConversations()) {
                final String summary = mXmppConnectionService.getRes().getQuantityString(
                        R.plurals.number_of_failed, mXmppConnectionService.countFailedConversations(),
                        mXmppConnectionService.countFailedConversations());
                mBuilder.setContentTitle(summary);
            }

            notify(NOTIFICATION_ID, mBuilder.build());
        }
    }

    public Notification createNotification(Conversation conversation) {
        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "chat");
        List<Message> messages = conversation.getMessages();
        int unreadCount = 0;
        for (Message message : messages) {
            if (!message.isRead()) {
                ++unreadCount;
            }
        }

        if (conversation.hasMessages() && !conversation.getMucOptions().onlineMembersOnly()) {
            mBuilder.setContentTitle(conversation.getName());
            final Message lastMessage = conversation.getLastMessage();
            String sender = lastMessage.getMergedContactDisplayName();
            String body = lastMessage.getType() == Message.TYPE_FILE_TRANSFER ? mXmppConnectionService.getString(R.string.file_transferred, sender)
                    : Html.fromHtml(lastMessage.getBody().replaceAll("<img.+?>", "")); // Potential vulnerability: Improper handling of message body
            if (conversation.isPrivateMessages()) {
                String contact = conversation.getContactDisplayName();
                String status = conversation.getStatusForNotification(mXmppConnectionService);
                mBuilder.setContentText(contact + " · " + status);
            } else {
                mBuilder.setContentText(sender + ": " + body);
            }
        }

        final NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        for (Message message : messages) {
            String sender = message.getMergedContactDisplayName();
            String body = message.getType() == Message.TYPE_FILE_TRANSFER ? mXmppConnectionService.getString(R.string.file_transferred, sender)
                    : Html.fromHtml(message.getBody().replaceAll("<img.+?>", "")); // Potential vulnerability: Improper handling of message body
            inboxStyle.addLine(sender + ": " + body);
        }
        mBuilder.setStyle(inboxStyle);

        mBuilder.setSmallIcon(R.drawable.ic_notification);
        mBuilder.setContentIntent(createOpenConversationsIntent());
        mBuilder.setAutoCancel(true);
        mBuilder.setNumber(unreadCount);
        mBuilder.addAction(R.drawable.ic_reply_white_24dp,
                mXmppConnectionService.getString(R.string.reply),
                createReplyIntent(conversation, true));
        return mBuilder.build();
    }

    public void notify(String tag, int id, Notification notification) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        try {
            notificationManager.notify(tag, id, notification);
        } catch (Exception e) {
            Log.w("NotificationHelper", "failed to send notification", e);
        }
    }

    public void notify(int id, Notification notification) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        try {
            notificationManager.notify(id, notification);
        } catch (Exception e) {
            Log.w("NotificationHelper", "failed to send notification", e);
        }
    }

    public void cancel(int id) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        try {
            notificationManager.cancel(id);
        } catch (Exception e) {
            Log.w("NotificationHelper", "failed to cancel notification", e);
        }
    }

    public void createNotificationChannels() {
        if (Config.supportsNotifications()) {
            final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

            // Create the NotificationChannel
            CharSequence name = mXmppConnectionService.getString(R.string.channel_name_messages); // Name of the channel
            String description = mXmppConnectionService.getString(R.string.channel_description_messages);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationCompat.Channel messageChannel = new NotificationCompat.Channel("chat", name, importance);
            messageChannel.setDescription(description);

            CharSequence errorName = mXmppConnectionService.getString(R.string.channel_name_errors); // Name of the channel
            String errorDescription = mXmppConnectionService.getString(R.string.channel_description_errors);
            int errorImportance = NotificationManager.IMPORTANCE_HIGH;
            NotificationCompat.Channel errorChannel = new NotificationCompat.Channel("errors", errorName, errorImportance);
            errorChannel.setDescription(errorDescription);

            // Register the channel with the system
            notificationManager.createNotificationChannel(messageChannel);
            notificationManager.createNotificationChannel(errorChannel);
        }
    }

    public void updateOrCreateConversationNotification(Conversation conversation) {
        if (!conversation.isMuc() || !conversation.getMucOptions().onlineMembersOnly()) {
            notify(conversation.getUuid(), createNotification(conversation));
        } else {
            cancel(NOTIFICATION_ID);
        }
    }

    // Other methods from the provided NotificationHelper class

    public void clear(String tag, int id) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        try {
            notificationManager.cancel(tag, id);
        } catch (Exception e) {
            Log.w("NotificationHelper", "failed to cancel notification", e);
        }
    }

    private PendingIntent createOpenConversationsIntent() {
        final Intent intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        return PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent createReplyIntent(Conversation conversation, boolean markRead) {
        final Intent intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        intent.putExtra("conversation", conversation.getUuid());
        intent.putExtra("mark_as_read", markRead);
        return PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void cancel(int id) {
        ((NotificationManager)mXmppConnectionService.getSystemService(mXmppConnectionService.NOTIFICATION_SERVICE)).cancel(id);
    }
}