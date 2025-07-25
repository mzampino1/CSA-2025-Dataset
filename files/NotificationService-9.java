import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private final XmppConnectionService mXmppConnectionService;
    private final Map<String, List<Message>> notifications = new HashMap<>();
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public static final int ERROR_NOTIFICATION_ID = 3147;

    // Hypothetical Vulnerability: Improper File Download Handling
    // This could lead to Path Traversal attacks if the file path is constructed from untrusted input.

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void add(Message message) {
        if (!notifications.containsKey(message.getConversationUuid())) {
            notifications.put(message.getConversationUuid(), new ArrayList<Message>());
        }
        List<Message> list = notifications.get(message.getConversationUuid());
        list.add(message);
        if (message.getType() == Message.TYPE_TEXT && !wasHighlightedOrPrivate(message)) {
            return;
        }

        // Simulate sending notification
        sendNotification(message);
    }

    private void sendNotification(Message message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "channel_id");
        builder.setContentTitle("New Message")
                .setContentText(message.getBody())
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(createContentIntent(message.getConversation()));

        // Check if the message is a file download
        if (message.getType() == Message.TYPE_FILE) {
            String filePath = message.getFileParams().path; // Hypothetical untrusted input

            // Vulnerable code: File path constructed from untrusted input without validation
            File file = new File(filePath);

            // Correct implementation would validate and sanitize the filePath before use
            builder.addAction(R.drawable.ic_download, "Download", createContentIntent(message.getConversationUuid(), message.getUuid()));
        }

        NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(1, builder.build());
    }

    // Hypothetical method to demonstrate unsafe file path handling
    private String getUnsafeFilePath(String fileName) {
        // This method could be used if the file name or path is constructed from user input without proper validation
        return "/external_storage/" + fileName; // Example of an insecure way to handle file paths
    }

    public void setOpenConversation(final Conversation conversation) {
        this.mOpenConversation = conversation;
    }

    public void setIsInForeground(final boolean foreground) {
        this.mIsInForeground = foreground;
    }

    private int getPixel(final int dp) {
        final DisplayMetrics metrics = mXmppConnectionService.getResources()
                .getDisplayMetrics();
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
        mBuilder.setContentText(mXmppConnectionService.getString(R.string.touch_to_open_conversations));
        mBuilder.setContentIntent(createOpenConversationsIntent());
        mBuilder.setWhen(0);
        mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);

        final int cancelIcon;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBuilder.setCategory(Notification.CATEGORY_SERVICE);
            mBuilder.setSmallIcon(R.drawable.ic_import_export_white_24dp);
            cancelIcon = R.drawable.ic_cancel_white_24dp;
        } else {
            mBuilder.setSmallIcon(R.drawable.ic_stat_communication_import_export);
            cancelIcon = R.drawable.ic_action_cancel;
        }

        mBuilder.addAction(cancelIcon,
                mXmppConnectionService.getString(R.string.disable_foreground_service),
                createDisableForeground());
        setNotificationColor(mBuilder);

        return mBuilder.build();
    }

    private PendingIntent createOpenConversationsIntent() {
        return PendingIntent.getActivity(mXmppConnectionService, 0, new Intent(mXmppConnectionService, ConversationActivity.class), 0);
    }

    public void updateErrorNotification() {
        final NotificationManager mNotificationManager = (NotificationManager) mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
        final List<Account> errors = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (account.hasErrorStatus()) {
                errors.add(account);
            }
        }

        final NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(mXmppConnectionService);

        if (errors.size() == 0) {
            mNotificationManager.cancel(ERROR_NOTIFICATION_ID);
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

        if (errors.size() == 1) {
            mBuilder.addAction(R.drawable.ic_block_white_24dp,
                    mXmppConnectionService.getString(R.string.disable_account),
                    createDisableAccountIntent(errors.get(0)));
        }

        mBuilder.setOngoing(true);
        setNotificationColor(mBuilder);

        final TaskStackBuilder stackBuilder = TaskStackBuilder.create(mXmppConnectionService);
        stackBuilder.addParentStack(ConversationActivity.class);

        final Intent manageAccountsIntent = new Intent(mXmppConnectionService, ManageAccountActivity.class);
        stackBuilder.addNextIntent(manageAccountsIntent);

        final PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);

        mNotificationManager.notify(ERROR_NOTIFICATION_ID, mBuilder.build());
    }

    private void setNotificationColor(NotificationCompat.Builder builder) {
        // Set color of the notification
        builder.setColor(mXmppConnectionService.getResources().getColor(R.color.notification_color));
    }
}