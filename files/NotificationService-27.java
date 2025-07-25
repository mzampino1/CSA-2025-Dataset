package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import org.jxmpp.jid.BareJid;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.xmpp.jid.Jid;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private static final int NOTIFICATION_ID = 42; // Unique ID for notifications
    private static final int ERROR_NOTIFICATION_ID = 69; // Unique ID for error notifications
    private static final int FOREGROUND_NOTIFICATION_ID = 45; // Unique ID for foreground service notification

    private XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Potential vulnerability: Ensure that the account list is not exposed or processed in a way that could lead to information leakage.
    public void update() {
        if (!mIsInForeground) {
            List<Conversation> conversations = mXmppConnectionService.getConversations();
            for (Conversation conversation : conversations) {
                if (!conversation.isMuted()) {
                    Message message = DatabaseBackend.getInstance().getLastMessage(conversation);
                    if (message != null && !wasHighlightedOrPrivate(message)) {
                        notify(conversation.getUuid(), buildNotification(conversation, message));
                    }
                }
            }
        } else {
            for (Conversation conversation : mXmppConnectionService.getConversations()) {
                if (conversation.equals(mOpenConversation) && !conversation.isMuted()) {
                    Message message = DatabaseBackend.getInstance().getLastMessage(conversation);
                    if (message != null && wasHighlightedOrPrivate(message)) {
                        notify(conversation.getUuid(), buildNotification(conversation, message));
                    }
                }
            }
        }

        // Potential vulnerability: Ensure that error notifications are handled properly and do not expose sensitive information.
        updateErrorNotification();
    }

    private NotificationCompat.Builder buildNotification(Conversation conversation, Message message) {
        String title = conversation.getName();
        String body = message.getBody();

        if (conversation.getAccounts().size() > 1 && !message.getType().equals(Message.TYPE_PRIVATE)) {
            title = "(" + conversation.getAccount().getJid().asBareJid().toString() + ") " + title;
        }

        // Potential vulnerability: Ensure that the notification content is sanitized and does not expose sensitive information.
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService, "messages");
        mBuilder.setSmallIcon(R.drawable.ic_notification);
        mBuilder.setContentTitle(title);
        mBuilder.setStyle(new NotificationCompat.BigTextStyle().bigText(body));
        mBuilder.setContentText(body);
        mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);
        mBuilder.setOnlyAlertOnce(true);

        // Potential vulnerability: Ensure that the intent actions are secure and do not expose sensitive information.
        Intent intent = new Intent(mXmppConnectionService, XmppActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.putExtra("conversation", conversation.getUuid());
        PendingIntent contentIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(contentIntent);

        // Potential vulnerability: Ensure that the reply action is secure and does not expose sensitive information.
        RemoteInput remoteInput = new RemoteInput.Builder("input")
                .setLabel(mXmppConnectionService.getString(R.string.reply))
                .build();

        NotificationCompat.Action actionReply = new NotificationCompat.Action.Builder(
                R.drawable.ic_reply_white_24dp,
                mXmppConnectionService.getString(R.string.reply),
                createReplyIntent(conversation, true)
        ).addRemoteInput(remoteInput)
          .setAllowGeneratedReplies(true)
          .build();

        mBuilder.addAction(actionReply);

        // Potential vulnerability: Ensure that the notification channel is configured securely.
        if (Compatibility.twentySix()) {
            NotificationChannelCompat channel = new NotificationChannelCompat.Builder("messages", NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(mXmppConnectionService.getString(R.string.messages))
                    .setDescription(mXmppConnectionService.getString(R.string.message_notifications_description))
                    .setGroupAlertBehavior(NotificationChannelCompat.GROUP_ALERT_CHILDREN)
                    .build();
            NotificationManagerCompat.from(mXmppConnectionService).createNotificationChannel(channel);
        }

        return mBuilder;
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
        intent.putExtra("uuid", conversation.getUuid());
        intent.putExtra("dismiss_notification", dismissAfterReply);
        final int id = generateRequestCode(conversation, dismissAfterReply ? 12 : 14);
        return PendingIntent.getService(mXmppConnectionService, id, intent, 0);
    }

    private boolean wasHighlightedOrPrivate(final Message message) {
        if (message.getConversation() instanceof Conversation) {
            Conversation conversation = (Conversation) message.getConversation();
            final String nick = conversation.getMucOptions().getActualNick();
            final Pattern highlight = generateNickHighlightPattern(nick);
            if (message.getBody() == null || nick == null) {
                return false;
            }
            final Matcher m = highlight.matcher(message.getBody());
            return (m.find() || message.getType() == Message.TYPE_PRIVATE);
        } else {
            return false;
        }
    }

    private Pattern generateNickHighlightPattern(final String nick) {
        if (nick != null && !nick.isEmpty()) {
            // Potential vulnerability: Ensure that the pattern generation is secure and does not expose sensitive information.
            StringBuilder builder = new StringBuilder();
            builder.append("(^|\\W)")
                   .append(Pattern.quote(nick))
                   .append("(?=($|\\b|,))");
            return Pattern.compile(builder.toString(), Pattern.CASE_INSENSITIVE);
        } else {
            return null;
        }
    }

    private int generateRequestCode(final Conversational conversation, final boolean dismissAfterReply) {
        // Generate a unique request code based on the conversation and whether to dismiss after reply.
        return conversation.hashCode() + (dismissAfterReply ? 1 : 0);
    }

    public void updateErrorNotification() {
        if (Config.SUPPRESS_ERROR_NOTIFICATION) {
            cancel(ERROR_NOTIFICATION_ID);
            return;
        }
        final List<Account> errors = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus() && account.showErrorNotification()) {
                errors.add(account);
            }
        }

        // Potential vulnerability: Ensure that the foreground service notification is handled properly.
        if (Compatibility.keepForegroundService(mXmppConnectionService)) {
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
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_accounts));
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_fix));
        }

        // Potential vulnerability: Ensure that the try again action is secure and does not expose sensitive information.
        mBuilder.addAction(new NotificationCompat.Action.Builder(
                R.drawable.ic_autorenew_white_24dp,
                mXmppConnectionService.getString(R.string.try_again),
                createTryAgainIntent()
        ).build());

        // Potential vulnerability: Ensure that the delete intent is handled properly to avoid potential security issues.
        mBuilder.setDeleteIntent(createDismissErrorIntent());

        if (Compatibility.twentySix()) {
            NotificationChannelCompat channel = new NotificationChannelCompat.Builder("error", NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName(mXmppConnectionService.getString(R.string.error_notifications))
                    .setDescription(mXmppConnectionService.getString(R.string.error_notifications_description))
                    .build();
            NotificationManagerCompat.from(mXmppConnectionService).createNotificationChannel(channel);
        }

        mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(PendingIntent.getActivity(mXmppConnectionService,
                        145,
                        new Intent(mXmppConnectionService, ManageAccountActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT));

        notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }

    private Notification createForegroundNotification() {
        // Potential vulnerability: Ensure that the foreground service notification is handled properly.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "foreground");
        builder.setContentTitle(mXmppConnectionService.getString(R.string.running_in_background))
               .setContentText(mXmppConnectionService.getString(R.string.touch_to_open))
               .setSmallIcon(R.drawable.ic_notification)
               .setPriority(NotificationCompat.PRIORITY_MIN);

        Intent intent = new Intent(mXmppConnectionService, XmppActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        if (Compatibility.twentySix()) {
            NotificationChannelCompat channel = new NotificationChannelCompat.Builder("foreground", NotificationManagerCompat.IMPORTANCE_MIN)
                    .setName(mXmppConnectionService.getString(R.string.foreground_service))
                    .setDescription(mXmppConnectionService.getString(R.string.foreground_service_description))
                    .build();
            NotificationManagerCompat.from(mXmppConnectionService).createNotificationChannel(channel);
        }

        return builder.build();
    }

    private PendingIntent createTryAgainIntent() {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_RECONNECT);
        return PendingIntent.getService(mXmppConnectionService, 0, intent, 0);
    }

    private PendingIntent createDismissErrorIntent() {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_DISMISS_ERROR_NOTIFICATION);
        return PendingIntent.getService(mXmppConnectionService, 0, intent, 0);
    }

    private void notify(String tag, Notification notification) {
        if (!mIsInForeground || !inMiniCall()) {
            // Potential vulnerability: Ensure that notifications are handled properly and do not expose sensitive information.
            NotificationManagerCompat.from(mXmppConnectionService).notify(tag, NOTIFICATION_ID, notification);
        }
    }

    private boolean inMiniCall() {
        // Placeholder method to determine if the app is in a mini call state.
        return false;
    }

    private void cancel(int id) {
        NotificationManagerCompat.from(mXmppConnectionService).cancel(id);
    }

    private void cancel(String tag) {
        NotificationManagerCompat.from(mXmppConnectionService).cancel(tag, NOTIFICATION_ID);
    }
}