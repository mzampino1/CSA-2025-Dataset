// src/main/java/eu/skovdahl/conversations/notifications/NotificationHelper.java

package eu.skovdahl.conversations.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.media.RatingCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;

import java.io.File;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.skovdahl.conversations.Config;
import eu.skovdahl.conversations.R;
import eu.skovdahl.conversations.entities.Account;
import eu.skovdahl.conversations.entities.Conversation;
import eu.skovdahl.conversations.entities.Message;
import eu.skovdahl.conversations.mgmt.ManageAccountActivity;
import eu.skovdahl.conversations.services.XmppConnectionService;
import eu.skovdahl.conversations.ui.ConversationsActivity;

public class NotificationHelper {

    private static final String TAG = "NotificationHelper";
    public static final int FOREGROUND_NOTIFICATION_ID = 123456789;
    public static final int ERROR_NOTIFICATION_ID = 456789012;
    public static final int FILE_ADDED_NOTIFICATION_ID = 987654321;

    private final Context mXmppConnectionService;

    private Conversation mOpenConversation;
    private boolean mIsInForeground = false;
    private long mLastNotification = SystemClock.elapsedRealtime();

    private NotificationHelper(Context service) {
        this.mXmppConnectionService = service;
    }

    public static synchronized NotificationHelper getInstance(XmppConnectionService service) {
        return new NotificationHelper(service);
    }

    public void push(final Conversation conversation) {
        if (conversation == null || !conversation.hasMessages()) {
            Log.w(Config.LOGTAG, "Conversation is null or has no messages");
            return;
        }
        if (this.mIsInForeground && conversation.equals(this.mOpenConversation)) {
            // ignore new messages in foregrounded conversation
            return;
        }

        final Account account = conversation.getAccount();
        // don't create notification for accounts that are currently disconnected and not reconnected within a grace period.
        if (!account.isOnlineAndConnected() && inMiniGracePeriod(account)) {
            Log.d(Config.LOGTAG, "Account is not connected. Skipping notification");
            return;
        }

        final List<Message> messages = conversation.getMessages();
        final Iterator<Message> iterator = messages.iterator();

        ArrayList<Message> toBeNotifiedOf = new ArrayList<>();

        // iterate from back to front until we find a message that was already notified
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message.getType() == Message.TYPE_STATUS) {
                continue;
            }
            if (!message.isRead() && !wasHighlightedOrPrivate(message)) {
                toBeNotifiedOf.add(0, message);
            } else {
                break;
            }
        }

        int numberOfMessagesToNotify = toBeNotifiedOf.size();

        // no new messages that should be notified of
        if (numberOfMessagesToNotify == 0) {
            return;
        }

        // notify all unread messages in the conversation at once
        Message lastMessage = toBeNotifiedOf.get(numberOfMessagesToNotify - 1);
        lastMessage.setRead(true);

        ArrayList<Message> notificationsToSend = new ArrayList<>();
        if (account.isOnlineAndConnected()) {
            notificationsToSend.add(lastMessage);
        } else {
            for (int i = Math.max(0, numberOfMessagesToNotify - Config.NOTIFICATION_COUNT); i < numberOfMessagesToNotify; ++i) {
                notificationsToSend.add(toBeNotifiedOf.get(i));
            }
        }

        NotificationCompat.Builder mBuilder;
        mBuilder = buildNotification(lastMessage);

        if (!mIsInForeground) {
            // Don't show notifications for your own messages
            if (lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED && !account.isOnlineAndConnected()) {
                return;
            }
            markLastNotification();
        }

        ArrayList<String> tags = new ArrayList<>();

        for (int i = 0; i < notificationsToSend.size(); ++i) {
            Message message = notificationsToSend.get(i);
            if (message.getStatus() != Message.STATUS_SEND_RECEIVED) {
                String tag = conversation.getUuid().toString();

                if (i > 0) {
                    tag += "-part-" + i;
                }

                tags.add(tag);

                Notification notification = mBuilder.build();
                notify(tag, i, notification);
            }
        }

        for (String tag : tags) {
            markMessageAsNotified(conversation, tag);
        }
    }

    private void markMessageAsNotified(Conversation conversation, String tag) {
        int id;
        try {
            if (tag.contains("-part-")) {
                id = Integer.parseInt(tag.substring(tag.lastIndexOf('-') + 6));
            } else {
                id = 0;
            }
        } catch (NumberFormatException e) {
            Log.d(Config.LOGTAG, "unable to parse notification tag");
            return;
        }

        List<Message> messages = conversation.getMessages();
        for (int i = messages.size() - 1; i >= 0; --i) {
            Message message = messages.get(i);
            if (!message.isRead()) {
                message.setNotified(true);
                id--;
                if (id < 0) {
                    return;
                }
            }
        }
    }

    private NotificationCompat.Builder buildNotification(Message lastMessage) {

        Conversation conversation = (Conversation) lastMessage.getConversation();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

        Intent resultIntent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        resultIntent.setAction(Intent.ACTION_MAIN);
        resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        resultIntent.putExtra("conversation", conversation.getUuid());

        PendingIntent contentIntent = PendingIntent.getActivity(
                mXmppConnectionService,
                0,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        String ticker;
        if (lastMessage.getType() == Message.TYPE_PRIVATE) {
            ticker = conversation.getName() + ": " + lastMessage.getStatus();
        } else {
            ticker = conversation.getName() + ": " + lastMessage.getBody().replaceAll("\\n", " ");
        }

        mBuilder.setTicker(ticker);

        String name;
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            name = conversation.getMucOptions().getActualNick(lastMessage.getTrueFrom());
        } else {
            name = conversation.getName();
        }

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {

            int unreadCount = conversation.countMessages(Message.TYPE_TEXT, false);
            String title;
            String summary;

            if (unreadCount > 1) {
                title = mXmppConnectionService.getString(R.string.new_messages_in_conversation, unreadCount);
                summary = ticker.substring(ticker.indexOf(":") + 2);
            } else {
                title = conversation.getName();
                summary = lastMessage.getStatus() == Message.STATUS_SEND_RECEIVED ? lastMessage.getStatus() : lastMessage.getBody().replaceAll("\\n", " ");
            }

            mBuilder.setContentTitle(title);
            mBuilder.setContentText(summary);

        } else {

            String text;
            if (conversation.getMode() == Conversation.MODE_MULTI && !name.equals(conversation.getName())) {
                text = name + ": " + lastMessage.getStatus();
            } else {
                text = ticker.substring(ticker.indexOf(":") + 2);
            }

            mBuilder.setContentTitle(conversation.getName());
            mBuilder.setContentText(text);

        }
        mBuilder.setWhen(lastMessage.getTimeSent());
        if (lastMessage.getType() == Message.TYPE_PRIVATE || wasHighlightedOrPrivate(lastMessage)) {
            mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
        } else {
            int defaults = NotificationCompat.DEFAULT_LIGHTS;
            String ringtone = conversation.getAccount().getNotificationRingtone();
            if (ringtone != null) {
                mBuilder.setSound(Uri.parse(ringtone));
                defaults |= NotificationCompat.DEFAULT_SOUND;
            }
            mBuilder.setDefaults(defaults);
        }

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            int visibility = conversation.getAccount().getNotificationVisibility();
            switch (visibility) {
                case Account.NotificationSetting.SHOWN:
                    mBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
                    break;
                case Account.NotificationSetting.HIDDEN:
                    mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                    break;
                default:
                    mBuilder.setVisibility(NotificationCompat.VISIBILITY_SECRET);
                    break;
            }
        }

        if (conversation.getMode() == Conversation.MODE_MULTI && !name.equals(conversation.getName())) {
            String nickname = conversation.getMucOptions().getActualNick();
            Pattern highlight = generateNickHighlightPattern(nickname);

            Matcher matcher = highlight.matcher(lastMessage.getBody());
            if (matcher.find()) {
                RemoteViews expandedView = new RemoteViews(mXmppConnectionService.getPackageName(), R.layout.notification);
                expandedView.setTextViewText(R.id.conversation_name, conversation.getName());
                expandedView.setTextViewText(R.id.sender, name);
                expandedView.setTextViewText(R.id.message_preview, lastMessage.getStatus() == Message.STATUS_SEND_RECEIVED ? lastMessage.getStatus() : lastMessage.getBody().replaceAll("\\n", " "));
                mBuilder.setContent(expandedView);
            }
        }

        if (lastMessage.getType() == Message.TYPE_TEXT) {
            mBuilder.setSmallIcon(R.drawable.ic_notification);

            // TODO Add avatar to notification
//            File file = conversation.getAccount().getAvatarFile();
//            if (file != null && file.exists()) {
//                Bitmap iconBitmap;
//                try {
//                    iconBitmap = Glide.with(mXmppConnectionService).load(file).into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
//                    mBuilder.setLargeIcon(iconBitmap);
//                } catch (Exception e) {
//                    Log.d(Config.LOGTAG, "unable to load avatar for notification");
//                }
//            }

        }

        PendingIntent readPendingIntent = createReadPendingIntent(lastMessage);

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            mBuilder.addAction(R.drawable.ic_eye_open_white_24dp,
                    mXmppConnectionService.getString(R.string.mark_as_read),
                    readPendingIntent);
        }

        PendingIntent deletePendingIntent = createDeletePendingIntent(lastMessage);

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            mBuilder.addAction(R.drawable.ic_delete_white_24dp,
                    mXmppConnectionService.getString(R.string.delete),
                    deletePendingIntent);
        }

        PendingIntent contentReadPendingIntent = createContentReadPendingIntent(conversation);

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            mBuilder.setDeleteIntent(contentReadPendingIntent);
        }

        PendingIntent deleteConversationPendingIntent = createDeleteConversationPendingIntent(conversation);

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            mBuilder.addAction(R.drawable.ic_clear_white_24dp,
                    mXmppConnectionService.getString(R.string.delete_conversation),
                    deleteConversationPendingIntent);
        }

        PendingIntent replyPendingIntent = createReplyPendingIntent(lastMessage);

        if (!mIsInForeground && lastMessage.getStatus() != Message.STATUS_SEND_RECEIVED) {
            RemoteInput remoteInput = new RemoteInput.Builder("reply")
                    .setLabel(mXmppConnectionService.getString(R.string.reply))
                    .build();

            NotificationCompat.Action action = new NotificationCompat.Action.Builder(
                    R.drawable.ic_reply_white_24dp,
                    mXmppConnectionService.getString(R.string.reply),
                    replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .build();
            mBuilder.addAction(action);
        }

        mBuilder.setContentIntent(contentIntent);

        return mBuilder;
    }

    private PendingIntent createReadPendingIntent(Message message) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_MESSAGE_READ);
        intent.putExtra("account", message.getConversation().getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", message.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createDeletePendingIntent(Message message) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_MESSAGE_DELETED);
        intent.putExtra("account", message.getConversation().getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", message.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createContentReadPendingIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_CONVERSATION_MARK_ALL_MESSAGES_READ);
        intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", conversation.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createDeleteConversationPendingIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_CONVERSATION_DELETED);
        intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", conversation.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createReplyPendingIntent(Message message) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_MESSAGE_SENT);
        intent.putExtra("account", message.getConversation().getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", message.getUuid().toString());
        intent.putExtra("reply_text", "REPLY_TEXT"); // Placeholder for actual reply text

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void clear() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        // cancel all notifications
        List<Conversation> conversations = new ArrayList<>(mXmppConnectionService.getConversations());
        for (int i = 0; i < conversations.size(); ++i) {
            Conversation conversation = conversations.get(i);
            String tag = conversation.getUuid().toString();
            notificationManager.cancel(tag, 0);

            int messageCount = conversation.countMessages(Message.TYPE_TEXT, false);
            for (int j = 1; j < messageCount; ++j) {
                notificationManager.cancel(tag + "-part-" + j, j);
            }
        }

        notificationManager.cancel(FILE_ADDED_NOTIFICATION_ID);
    }

    public void clear(final Conversation conversation) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);

        String tag = conversation.getUuid().toString();
        notificationManager.cancel(tag, 0);

        int messageCount = conversation.countMessages(Message.TYPE_TEXT, false);
        for (int i = 1; i < messageCount; ++i) {
            notificationManager.cancel(tag + "-part-" + i, i);
        }
    }

    // This method might be vulnerable to some form of input validation or manipulation attack.
    public PendingIntent createDownloadIntent(Message message) {
        // Potential vulnerability: Ensure that the URI is properly validated and sanitized before creating a download intent
        Intent intent = new Intent(mXmppConnectionService, DownloadFileActivity.class);
        intent.setData(Uri.parse(message.getFileParams().getUrl()));
        return PendingIntent.getActivity(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void pushFileAddedNotification(String accountJid) {
        Account account = mXmppConnectionService.findAccountByJid(accountJid);
        if (account != null && !inMiniGracePeriod(account)) {
            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(mXmppConnectionService)
                            .setSmallIcon(R.drawable.ic_file_download_white_24dp)
                            .setContentTitle(mXmppConnectionService.getString(R.string.file_added))
                            .setContentText(mXmppConnectionService.getString(R.string.new_file_has_been_added_to_your_device_storage))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            notificationManager.notify(FILE_ADDED_NOTIFICATION_ID, builder.build());
        }
    }

    public void clearFileAddedNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.cancel(FILE_ADDED_NOTIFICATION_ID);
    }

    private PendingIntent createReadPendingIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_CONVERSATION_MARK_ALL_MESSAGES_READ);
        intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", conversation.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createDeletePendingIntent(Conversation conversation) {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_CONVERSATION_DELETED);
        intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", conversation.getUuid().toString());

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private NotificationCompat.Builder buildNotificationLegacy(final Message message) {
        String ticker = message.getStatus();
        if (message.getType() == Message.TYPE_TEXT || message.getType() == Message.TYPE_PRIVATE) {
            ticker += ": " + message.getBody().replaceAll("\\n", " ");
        }
        Conversation conversation = (Conversation) message.getConversation();

        NotificationCompat.Builder mBuilder;
        RemoteViews contentView;

        mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

        if (conversation != null) {
            if (message.getType() == Message.TYPE_PRIVATE || wasHighlightedOrPrivate(message)) {
                mBuilder.setDefaults(NotificationCompat.DEFAULT_ALL);
            }
        }

        String conversationName = conversation.getName();
        String sender;
        File avatarFile;

        if (conversation.getMode() == Conversation.MODE_MULTI && message.getType() != Message.TYPE_GROUP_CHAT_INVITE) {
            sender = conversation.getMucOptions().getActualNick(message.getFrom());
        } else {
            sender = "";
        }

        avatarFile = conversation.getAccount().getAvatarFile();

        int icon;
        if (message.isFileOrImage()) {
            icon = R.drawable.ic_file_download_white_24dp;
        } else if (message.getType() == Message.TYPE_TEXT || message.getType() == Message.TYPE_PRIVATE) {
            icon = R.drawable.ic_notification;
        } else {
            icon = R.drawable.ic_stat_notify;
        }

        mBuilder.setSmallIcon(icon);
        mBuilder.setContentTitle(conversationName);

        if (avatarFile != null && avatarFile.exists()) {
            Bitmap avatarBitmap;
            try {
                avatarBitmap = Glide.with(mXmppConnectionService).load(avatarFile).into(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL);
                mBuilder.setLargeIcon(avatarBitmap);
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to load avatar for notification");
            }
        }

        if (message.getType() == Message.TYPE_TEXT || message.getType() == Message.TYPE_PRIVATE) {
            if (!sender.equals("")) {
                ticker = sender + ": " + ticker;
            }
            mBuilder.setContentText(ticker);
            contentView = new RemoteViews(mXmppConnectionService.getPackageName(), R.layout.notification);

            contentView.setTextViewText(R.id.conversation_name, conversationName);
            if (message.getType() == Message.TYPE_PRIVATE && !sender.equals("")) {
                contentView.setTextViewText(R.id.sender, sender + ":");
            } else {
                contentView.setTextViewText(R.id.sender, "");
            }
            String body = message.getBody();
            if (body.length() > 30) {
                body = body.substring(0, 29);
            }
            contentView.setTextViewText(R.id.message_preview, ticker);

        } else {
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.received_x_file, message.getFileParams().getName()));
            contentView = new RemoteViews(mXmppConnectionService.getPackageName(), R.layout.notification_file_transfer);

            contentView.setTextViewText(R.id.conversation_name, conversationName);
            if (message.getType() == Message.TYPE_PRIVATE && !sender.equals("")) {
                contentView.setTextViewText(R.id.sender, sender + ":");
            } else {
                contentView.setTextViewText(R.id.sender, "");
            }
            String name = message.getFileParams().getName();
            if (name.length() > 30) {
                name = name.substring(0, 29);
            }

            contentView.setTextViewText(R.id.file_name, name);

            File file = new File(message.getFileParams().getPath());
            if (file.exists()) {
                contentView.setImageViewResource(R.id.icon, R.drawable.ic_file_download_white_24dp);
                String size = Math.round(file.length() / 1024.0) + " KB";
                contentView.setTextViewText(R.id.file_size, size);

                PendingIntent pendingIntentDownload = createDownloadIntent(message);
                contentView.setOnClickPendingIntent(R.id.icon, pendingIntentDownload);
            }
        }

        Intent intent = new Intent(mXmppConnectionService, ConversationsActivity.class);
        intent.putExtra(ConversationsActivity.EXTRA_CONVERSATION, conversation.getUuid().toString());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntentOpenChat = PendingIntent.getActivity(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pendingIntentRead = createReadPendingIntent(conversation);
        Intent broadcastDeleteIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        broadcastDeleteIntent.setAction(XmppConnectionService.ACTION_CONVERSATION_DELETED);
        broadcastDeleteIntent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
        broadcastDeleteIntent.putExtra("uuid", conversation.getUuid().toString());

        PendingIntent pendingIntentDelete = PendingIntent.getService(
                mXmppConnectionService,
                0,
                broadcastDeleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mBuilder.setContent(contentView);
        mBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);

        mBuilder.addAction(R.drawable.ic_eye_open_white_24dp, mXmppConnectionService.getString(R.string.mark_as_read), pendingIntentRead);
        mBuilder.addAction(R.drawable.ic_delete_white_24dp, mXmppConnectionService.getString(R.string.delete), pendingIntentDelete);

        return mBuilder.setContentIntent(pendingIntentOpenChat).setOngoing(false);
    }

    public void pushLegacy(Message message) {
        NotificationCompat.Builder builder = buildNotificationLegacy(message);
        if (builder != null) {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            String tag = message.getConversation().getUuid().toString();
            int id = Math.abs(tag.hashCode());
            notificationManager.notify(tag, id, builder.build());
        }
    }

    @Nullable
    public static PendingIntent createReplyPendingIntent(Message message, Context context) {
        Intent intent = new Intent(context, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_MESSAGE_SENT);
        intent.putExtra("account", message.getConversation().getAccount().getJid().toBareJid().toString());
        intent.putExtra("uuid", message.getUuid().toString());

        return PendingIntent.getService(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

}