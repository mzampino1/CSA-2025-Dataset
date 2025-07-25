package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.util.DisplayMetrics;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.geo.GeoHelper;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.utils.AccountUtils;

public class NotificationService {

    private final XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation = null;
    private boolean mIsInForeground = false;
    private long mLastNotification = 0;
    public static final int FOREGROUND_NOTIFICATION_ID = 42;
    public static final int ERROR_NOTIFICATION_ID = 13;

    public NotificationService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void addMessageNotification(Message message, Account account) {
        if (mIsInForeground && inMiniGracePeriod(account)) {
            return;
        }
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        Conversation conversation = message.getConversation();
        String subject = null;

        if (!conversation.isPrivateChat() && wasHighlightedOrPrivate(message)) {
            subject = mXmppConnectionService.getString(R.string.highlighted_message_from_n,
                    AccountUtils.getDisplayName(account));
        } else {
            subject = account.getJid().asBareJid().toString();
        }

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        if (message.getType() == Message.TYPE_PRIVATE) {
            builder.setSmallIcon(R.drawable.ic_lock_white_24dp);
        } else {
            builder.setSmallIcon(R.drawable.ic_chat_white_24dp);
        }
        final Intent intent;
        if (!conversation.isPrivateChat()) {
            intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            intent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);
            intent.putExtra("uuid", conversation.getUuid());
            builder.setContentIntent(PendingIntent.getActivity(
                    mXmppConnectionService,
                    conversation.getUuid().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            intent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);
            intent.putExtra("uuid", conversation.getUuid());
            builder.setContentIntent(PendingIntent.getActivity(
                    mXmppConnectionService,
                    conversation.getUuid().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT));
        }
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setContentTitle(subject);
        builder.setContentText(message.getBody());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
        }

        notificationManager.notify(conversation.getUuid().hashCode() % 4927, builder.build());
        markLastNotification();
    }

    public void clearMessageNotifications(String uuid) {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        notificationManager.cancel(uuid.hashCode() % 4927);
        if (uuid.equals(this.mOpenConversation.getUuid())) {
            this.mOpenConversation = null;
        }
    }

    public void clearErrorNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus()) {
                account.setErrorStatusShown(false);
            }
        }
        notificationManager.cancel(ERROR_NOTIFICATION_ID);
    }

    public void updateMessageNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        Conversation conversation = mOpenConversation;
        if (conversation == null) {
            return;
        }
        int count = 0;
        Message message = null;
        for (final Message msg : conversation.getMessages()) {
            if (!msg.isRead() && msg.getType() != Message.TYPE_STATUS) {
                ++count;
                message = msg;
            }
        }

        final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
        String subject = null;

        if (message == null) {
            notificationManager.cancel(conversation.getUuid().hashCode());
            return;
        } else if (!conversation.isPrivateChat() && wasHighlightedOrPrivate(message)) {
            subject = mXmppConnectionService.getString(R.string.highlighted_message_from_n,
                    AccountUtils.getDisplayName(conversation.getAccount()));
        } else {
            subject = conversation.getName();
        }

        final Intent intent;
        if (!conversation.isPrivateChat()) {
            intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            intent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);
            intent.putExtra("uuid", conversation.getUuid());
            builder.setContentIntent(PendingIntent.getActivity(
                    mXmppConnectionService,
                    conversation.getUuid().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            intent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);
            intent.putExtra("uuid", conversation.getUuid());
            builder.setContentIntent(PendingIntent.getActivity(
                    mXmppConnectionService,
                    conversation.getUuid().hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT));
        }
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setContentTitle(subject);
        builder.setContentText(message.getBody());

        if (count > 1) {
            final NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            style.addLine(subject + ": " + message.getBody());
            for (final Message msg : conversation.getMessages()) {
                if (!msg.isRead() && msg.getType() != Message.TYPE_STATUS) {
                    String sender;
                    if (conversation.isPrivateChat()) {
                        sender = "";
                    } else {
                        sender = msg.getDisplayName();
                    }
                    style.addLine(sender + ": " + msg.getBody());
                    ++count;
                }
            }
            builder.setStyle(style);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setCategory(NotificationCompat.CATEGORY_MESSAGE);
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
        }

        notificationManager.notify(conversation.getUuid().hashCode() % 4927, builder.build());
    }

    public void updateErrorNotification() {
        final NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        final List<Account> errors = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus() && account.showErrorNotification()) {
                errors.add(account);
            }
        }

        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);
        if (errors.size() == 0) {
            notificationManager.cancel(ERROR_NOTIFICATION_ID);
            return;
        } else if (errors.size() == 1) {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_account));
            mBuilder.setContentText(errors.get(0).getJid().toBareJid().toString());
        } else {
            mBuilder.setContentTitle(mXmppConnectionService.getString(R.string.problem_connecting_to_accounts));
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_fix));
        }
        mBuilder.addAction(R.drawable.ic_autorenew_white_24dp,
                mXmppConnectionService.getString(R.string.try_again),
                createTryAgainIntent());
        mBuilder.setDeleteIntent(createDismissErrorIntent());
        mBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setSmallIcon(R.drawable.ic_warning_white_24dp);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning);
        }
        mBuilder.setContentIntent(PendingIntent.getActivity(mXmppConnectionService,
                145,
                new Intent(mXmppConnectionService,ManageAccountActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        notificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
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
                } else if (!account.isOptionSet(Account.OPTION_DISABLED)) {
                    enabled++;
                }
            }
            mBuilder.setContentText(mXmppConnectionService.getString(R.string.connected_to_d_of_d_accounts,
                    connected, enabled));
        } else {
            mBuilder.setContentText("");
        }

        Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
        intent.setAction(ConversationActivity.ACTION_VIEW_CONVERSATION);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        mBuilder.setContentIntent(pendingIntent);
        mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);
        }

        mBuilder.addAction(R.drawable.ic_lock_white_24dp,
                mXmppConnectionService.getString(R.string.lock_app),
                createLockIntent());

        return mBuilder.build();
    }

    private PendingIntent createTryAgainIntent() {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_TRY_AGAIN);

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createDismissErrorIntent() {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_DISMISS_ERROR);

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private PendingIntent createLockIntent() {
        Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_LOCK);

        return PendingIntent.getService(
                mXmppConnectionService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );
    }

    private void markLastNotification() {
        this.mLastNotification = System.currentTimeMillis();
    }

    private boolean inMiniGracePeriod(Account account) {
        long timeSinceLastNotification = System.currentTimeMillis() - mLastNotification;
        return timeSinceLastNotification < Config.MINI_GRACE_PERIOD * 1000;
    }

    private boolean wasHighlightedOrPrivate(Message message) {
        String nick = message.getConversation().getMucOptions().getActualNick();
        if (nick != null && message.getBodyAsTrustedString().contains(nick)) {
            return true;
        } else {
            return message.getType() == Message.TYPE_PRIVATE;
        }
    }

    public void setOpenConversation(Conversation conversation) {
        this.mOpenConversation = conversation;
    }

    public Conversation getOpenConversation() {
        return this.mOpenConversation;
    }

    public void setIsInForeground(boolean isInForeground) {
        this.mIsInForeground = isInForeground;
    }

    public boolean isInForeground() {
        return this.mIsInForeground;
    }
}