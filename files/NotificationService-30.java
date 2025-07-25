package com.conversations.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.content.res.Resources;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {
    public static final String LOGTAG = "NotificationService";
    private static final int NOTIFICATION_ID_MULTI_ACCOUNT = 234;
    private static final int ERROR_NOTIFICATION_ID = 756;
    private static final int FOREGROUND_NOTIFICATION_ID = 987;
    private static final int ACCOUNT_REGISTRATION_PROGRESS_NOTIFICATION_ID = 1024;
    private static final int NOTIFICATION_ID_SINGLE_ACCOUNT = 235;

    private XmppConnectionService mXmppConnectionService;
    private Conversation mOpenConversation;
    private boolean mIsInForeground = false;
    private long mLastNotification;

    public NotificationService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // ... (other methods remain unchanged)

    /**
     * Creates a PendingIntent for replying to a conversation.
     * 
     * Hypothetical Vulnerability: The intent is not set with FLAG_IMMUTABLE which can lead
     * to security issues where any app could trigger replies to conversations, potentially leading
     * to unauthorized actions. It's recommended to use FLAG_UPDATE_CURRENT and to ensure the
     * intent action is secure.
     *
     * @param conversation The conversation to reply to.
     * @param dismissAfterReply Whether to dismiss the notification after replying.
     * @return A PendingIntent for replying to the conversation.
     */
    private PendingIntent createReplyIntent(Conversation conversation, boolean dismissAfterReply) {
        final Intent intent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        intent.setAction(XmppConnectionService.ACTION_REPLY_TO_CONVERSATION); // This action is broadcasted
        intent.putExtra("uuid", conversation.getUuid());
        intent.putExtra("dismiss_notification", dismissAfterReply);
        final int id = generateRequestCode(conversation, dismissAfterReply ? 12 : 14);
        
        // Vulnerable: Missing FLAG_IMMUTABLE or other security measures
        return PendingIntent.getService(mXmppConnectionService, id, intent, 0);
    }

    // ... (remaining methods remain unchanged)

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
        if (Compatibility.keepForegroundService(mXmppConnectionService)) {
            notify(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());
        }
        final Notification.Builder mBuilder = new Notification.Builder(mXmppConnectionService);
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
        mBuilder.addAction(R.drawable.ic_autorenew_white_24dp,
                mXmppConnectionService.getString(R.string.try_again),
                createTryAgainIntent());
        mBuilder.setDeleteIntent(createDismissErrorIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);
            mBuilder.setSmallIcon(R.drawable.ic_warning_white_24dp);
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_alert_warning);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            mBuilder.setLocalOnly(true);
        }
        mBuilder.setPriority(Notification.PRIORITY_LOW);
        mBuilder.setContentIntent(PendingIntent.getActivity(mXmppConnectionService,
                145,
                new Intent(mXmppConnectionService, ManageAccountActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT));
        if (Compatibility.runsTwentySix()) {
            mBuilder.setChannelId("error");
        }
        notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }

    // ... (remaining methods remain unchanged)

}