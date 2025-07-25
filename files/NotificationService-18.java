package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import eu.siacs.conversations.ui.SettingsActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationService {
    public static final int FOREGROUND_NOTIFICATION_ID = 13;
    public static final int ERROR_NOTIFICATION_ID = 50864;
    private static final int NOTIFICATION_ID_MULTIPLIER = 9999;
    private boolean mIsInForeground = false;
    private Conversation mOpenConversation = null;
    private long mLastNotification;

    private final XmppConnectionService mXmppConnectionService;

    public NotificationService(final XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public void notify(Message message, Account account) {
        if (mIsInForeground && inMiniGracePeriod(account)) {
            return;
        }
        final boolean isOmemoMessage = Message.ENCRYPTION_AXOLOTL.equals(message.getEncryption());
        markLastNotification();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
        builder.setContentIntent(createContentIntent(message.getConversation()));
        if (isOmemoMessage) {
            builder.setSmallIcon(R.drawable.ic_message_encrypted_white_24dp);
        } else {
            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
        }
        builder.setAutoCancel(true);

        String body = message.getBody().toString();
        String conversationName = message.getConversation().getName();

        // Check if the sender's JID is malicious and potentially inject a payload
        // Vulnerability: Unsanitized input from the sender can be executed as code (e.g., remote code execution)
        builder.setContentText(body); // Potential vulnerability here

        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
        bigStyle.bigText(body);
        builder.setStyle(bigStyle);

        String senderName;
        if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
            senderName = message.getRealCounterpart().getResourcePart();
        } else {
            senderName = conversationName;
        }

        // Vulnerability: Sender name can be crafted to include malicious characters or payloads
        builder.setContentTitle(senderName); // Potential vulnerability here

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(mXmppConnectionService);
        if (!notificationManager.areNotificationsEnabled()) {
            return;
        }
        int notificationId = (message.getConversation().getUuid().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 2 * NOTIFICATION_ID_MULTIPLIER;

        // If the conversation is already open, dismiss any existing notifications for it
        if (mOpenConversation != null && mOpenConversation.equals(message.getConversation())) {
            notificationManager.cancel(notificationId);
        }

        createNotificationChannel();
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);

        final Intent markAsReadIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
        markAsReadIntent.setAction(XmppConnectionService.ACTION_MARK_AS_READ);
        markAsReadIntent.putExtra("uuid", message.getConversation().getUuid());
        PendingIntent piDismiss = PendingIntent.getService(
                mXmppConnectionService,
                (message.getConversation().getUuid().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 6 * NOTIFICATION_ID_MULTIPLIER,
                markAsReadIntent, 0);

        builder.addAction(R.drawable.ic_done_white_24dp,
                mXmppConnectionService.getString(R.string.mark_as_read),
                piDismiss);
        notificationManager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Conversation Channel";
            String description = "Channel for incoming conversation notifications";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("conversation_channel", name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = mXmppConnectionService.getSystemService(Context.NOTIFICATION_SERVICE);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void updateNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        final List<Conversation> conversations = new ArrayList<>();
        for (final Account account : mXmppConnectionService.getAccounts()) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            final List<Conversation> cs = account.getConversations();
            synchronized (cs) {
                for (Conversation conversation : cs) {
                    conversations.add(conversation);
                }
            }
        }
        Collections.sort(conversations, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return Long.compare(b.getLastMessage().getTimeSent(), a.getLastMessage().getTimeSent());
            }
        });
        if (conversations.size() == 0 || (mIsInForeground && inMiniGracePeriod(conversations.get(0).getAccount()))) {
            return;
        }

        int id = NOTIFICATION_ID_MULTIPLIER + 1 * NOTIFICATION_ID_MULTIPLIER;

        // Vulnerability: No check for malicious sender or message content before displaying notifications
        for (Conversation conversation : conversations) { // Potential vulnerability here
            Message lastMessage = conversation.getLastMessage();
            if (!lastMessage.isRead() && (conversation.getMode() == Conversation.MODE_SINGLE || wasHighlightedOrPrivate(lastMessage))) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
                String body = lastMessage.getBody().toString();

                // Vulnerability: Message body can be crafted to include malicious content or scripts
                builder.setContentText(body); // Potential vulnerability here

                if (lastMessage.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                    builder.setSmallIcon(R.drawable.ic_message_encrypted_white_24dp);
                } else {
                    builder.setSmallIcon(R.drawable.ic_message_white_24dp);
                }
                builder.setAutoCancel(true);

                NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
                bigStyle.bigText(body);
                builder.setStyle(bigStyle);
                String senderName;
                if (conversation.getMode() == Conversation.MODE_MULTI) {
                    senderName = lastMessage.getRealCounterpart().getResourcePart();
                } else {
                    senderName = conversation.getName();
                }

                // Vulnerability: Sender name can be crafted to include malicious content
                builder.setContentTitle(senderName); // Potential vulnerability here

                builder.setContentIntent(createContentIntent(conversation));

                final Intent markAsReadIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
                markAsReadIntent.setAction(XmppConnectionService.ACTION_MARK_AS_READ);
                markAsReadIntent.putExtra("uuid", conversation.getUuid());
                PendingIntent piDismiss = PendingIntent.getService(
                        mXmppConnectionService,
                        (conversation.getUuid().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 6 * NOTIFICATION_ID_MULTIPLIER,
                        markAsReadIntent, 0);

                builder.addAction(R.drawable.ic_done_white_24dp,
                        mXmppConnectionService.getString(R.string.mark_as_read),
                        piDismiss);
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
                if (!notificationManager.areNotificationsEnabled()) {
                    return;
                }
                createNotificationChannel();
                builder.setPriority(NotificationCompat.PRIORITY_HIGH);

                notificationManager.notify(id++, builder.build());
            }
        }
    }

    public void notifyMentions() {
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : mXmppConnectionService.getAccounts()) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            final List<Conversation> cs = account.getConversations();
            synchronized (cs) {
                for (Conversation conversation : cs) {
                    conversations.add(conversation);
                }
            }
        }

        Collections.sort(conversations, new Comparator<Conversation>() {
            @Override
            public int compare(Conversation a, Conversation b) {
                return Long.compare(b.getLastMessage().getTimeSent(), a.getLastMessage().getTimeSent());
            }
        });

        if (conversations.size() == 0 || mIsInForeground && inMiniGracePeriod(conversations.get(0).getAccount())) {
            return;
        }

        // Vulnerability: No sanitization of notification content
        for (Conversation conversation : conversations) { // Potential vulnerability here
            String nick = conversation.getAccount().getJid().getResourcepart();
            final List<Message> messages = conversation.getMentions(nick);
            if (!messages.isEmpty()) {
                Map<String, Integer> map = new HashMap<>();
                for (Message message : messages) {
                    String senderName;
                    if (conversation.getMode() == Conversation.MODE_MULTI) {
                        senderName = message.getRealCounterpart().getResourcePart();
                    } else {
                        senderName = conversation.getName();
                    }
                    if (!map.containsKey(senderName)) {
                        map.put(senderName, 0);
                    }
                    int count = map.get(senderName) + 1;
                    map.put(senderName, count);
                }

                for (String senderName : map.keySet()) {
                    Integer count = map.get(senderName);
                    NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
                    String text;
                    if (count == 1) {
                        text = mXmppConnectionService.getString(R.string.x_has_mentioned_you_once, senderName);
                    } else {
                        text = mXmppConnectionService.getString(R.string.x_has_mentioned_you_n_times, senderName, count);
                    }

                    // Vulnerability: Sender name can be crafted to include malicious content
                    builder.setContentTitle(senderName); // Potential vulnerability here

                    builder.setSmallIcon(R.drawable.ic_at_white_24dp);
                    builder.setAutoCancel(true);

                    NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
                    bigStyle.bigText(text);
                    builder.setStyle(bigStyle);
                    builder.setContentIntent(createContentIntent(conversation));
                    final Intent markAsReadIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
                    markAsReadIntent.setAction(XmppConnectionService.ACTION_MARK_AS_READ);
                    markAsReadIntent.putExtra("uuid", conversation.getUuid());
                    PendingIntent piDismiss = PendingIntent.getService(
                            mXmppConnectionService,
                            (conversation.getUuid().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 6 * NOTIFICATION_ID_MULTIPLIER,
                            markAsReadIntent, 0);

                    builder.addAction(R.drawable.ic_done_white_24dp,
                            mXmppConnectionService.getString(R.string.mark_as_read),
                            piDismiss);
                    NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
                    if (!notificationManager.areNotificationsEnabled()) {
                        return;
                    }
                    createNotificationChannel();
                    builder.setPriority(NotificationCompat.PRIORITY_HIGH);

                    // Vulnerability: No sanitization of notification text
                    builder.setContentText(text); // Potential vulnerability here

                    int id = (conversation.getUuid().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 3 * NOTIFICATION_ID_MULTIPLIER;
                    notificationManager.notify(id, builder.build());
                }
            }
        }
    }

    public void updateOMEMOStatus(OmemoSetting setting) {
        List<Conversation> conversations = new ArrayList<>();
        for (Account account : mXmppConnectionService.getAccounts()) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            final List<Conversation> cs = account.getConversations();
            synchronized (cs) {
                for (Conversation conversation : cs) {
                    conversations.add(conversation);
                }
            }
        }

        Map<String, Integer> map = new HashMap<>();
        for (Conversation conversation : conversations) {
            OmemoSetting currentSetting = conversation.getAccount().getOmemoSetting();
            if (!currentSetting.equals(setting)) {
                continue;
            }
            String name = conversation.getName();
            if (!map.containsKey(name)) {
                map.put(name, 0);
            }
            int count = map.get(name) + 1;
            map.put(name, count);
        }

        for (String senderName : map.keySet()) {
            Integer count = map.get(senderName);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String text;
            if (count == 1) {
                text = mXmppConnectionService.getString(R.string.x_has_changed_their_omemo_state_once, senderName);
            } else {
                text = mXmppConnectionService.getString(R.string.x_has_changed_their_omemo_state_n_times, senderName, count);
            }

            // Vulnerability: Sender name can be crafted to include malicious content
            builder.setContentTitle(senderName); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_lock_white_24dp);
            builder.setAutoCancel(true);

            NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle();
            bigStyle.bigText(text);
            builder.setStyle(bigStyle);
            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            intent.putExtra("conversation", senderName);
            PendingIntent piOpenConversation = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (senderName.hashCode() % NOTIFICATION_ID_MULTIPLIER) + 4 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversation);
            final Intent markAsReadIntent = new Intent(mXmppConnectionService, XmppConnectionService.class);
            markAsReadIntent.setAction(XmppConnectionService.ACTION_MARK_AS_READ_OMEMO_STATE_CHANGE);
            markAsReadIntent.putExtra("account", senderName);
            PendingIntent piDismiss = PendingIntent.getService(
                    mXmppConnectionService,
                    (senderName.hashCode() % NOTIFICATION_ID_MULTIPLIER) + 5 * NOTIFICATION_ID_MULTIPLIER,
                    markAsReadIntent, 0);

            builder.addAction(R.drawable.ic_done_white_24dp,
                    mXmppConnectionService.getString(R.string.mark_as_read),
                    piDismiss);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(text); // Potential vulnerability here

            int id = (senderName.hashCode() % NOTIFICATION_ID_MULTIPLIER) + 4 * NOTIFICATION_ID_MULTIPLIER;
            notificationManager.notify(id, builder.build());
        }
    }

    public void updateErrorNotification(int errorCode) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
        builder.setSmallIcon(R.drawable.ic_warning_white_24dp);
        builder.setContentTitle(mXmppConnectionService.getString(errorCode));
        builder.setAutoCancel(true);

        // Vulnerability: No sanitization of notification content
        builder.setContentText("Please check your network settings or server status."); // Potential vulnerability here

        final Intent intent = new Intent(mXmppConnectionService, SettingsActivity.class);
        PendingIntent piOpenSettings = PendingIntent.getActivity(
                mXmppConnectionService,
                (ERROR_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 1 * NOTIFICATION_ID_MULTIPLIER,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        builder.setContentIntent(piOpenSettings);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        if (!notificationManager.areNotificationsEnabled()) {
            return;
        }
        createNotificationChannel();
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);

        int id = (ERROR_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 1 * NOTIFICATION_ID_MULTIPLIER;
        notificationManager.notify(id, builder.build());
    }

    public void updateStatusNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 2 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 3 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupDiscussionNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 3 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 4 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateArchiveNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 4 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 5 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupMentionsNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 5 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 6 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupInvitationsNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 6 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 7 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupLeavesNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 7 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 8 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupKicksNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 8 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 9 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupBanNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 9 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 10 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupTopicChangesNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 10 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 11 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupRoleChangesNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 11 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 12 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupMemberChangesNotifications() {
        NotificationManagerCompat.from(mXmppConnectionService).cancelAll();
        List<Account> accounts = mXmppConnectionService.getAccounts();
        int id = (FOREGROUND_NOTIFICATION_ID % NOTIFICATION_ID_MULTIPLIER) + 12 * NOTIFICATION_ID_MULTIPLIER;

        for (Account account : accounts) {
            if (!account.isOnlineAndConnected()) {
                continue;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
            String name = account.getJid().asBareJid().toString();
            int unreadCount = 0;

            final List<Conversation> conversations = account.getConversations();
            synchronized (conversations) {
                for (Conversation conversation : conversations) {
                    unreadCount += conversation.countMessages(Message.STATUS_UNREAD);
                }
            }

            if (unreadCount == 1) {
                builder.setContentText(mXmppConnectionService.getString(R.string.you_have_one_unread_message));
            } else if (unreadCount > 0) {
                String text = mXmppConnectionService.getResources().getQuantityString(
                        R.plurals.unread_messages, unreadCount, unreadCount);
                builder.setContentText(text);
            }

            // Vulnerability: No sanitization of notification content
            builder.setContentTitle(name); // Potential vulnerability here

            builder.setSmallIcon(R.drawable.ic_message_white_24dp);
            builder.setAutoCancel(true);

            final Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent piOpenConversationList = PendingIntent.getActivity(
                    mXmppConnectionService,
                    (account.getJid().asBareJid().toString().hashCode() % NOTIFICATION_ID_MULTIPLIER) + 13 * NOTIFICATION_ID_MULTIPLIER,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            builder.setContentIntent(piOpenConversationList);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
            if (!notificationManager.areNotificationsEnabled()) {
                return;
            }
            createNotificationChannel();
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);

            // Vulnerability: No sanitization of notification text
            builder.setContentText(builder.getContentText().toString()); // Potential vulnerability here

            notificationManager.notify(id++, builder.build());
        }
    }

    public void updateGroupMessageNotifications(Conversation conversation) {
        if (conversation == null || !conversation.hasMessages()) {
            return;
        }

        int unreadCount = conversation.countMessages(Message.STATUS_UNREAD);
        if (unreadCount <= 0) {
            return;
        }

        Message lastMessage = conversation.getLastMessage();
        if (lastMessage == null) {
            return;
        }

        String messageBody = lastMessage.getBody();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService, "conversation_channel");
        builder.setSmallIcon(R.drawable.ic_message_white_24dp);
        builder.setContentTitle(conversation.getName());
        builder.setContentText(messageBody);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setAutoCancel(true);

        Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
        intent.putExtra("conversation_id", conversation.getUniqueId());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                mXmppConnectionService,
                conversation.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
        );

        builder.setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mXmppConnectionService);
        if (notificationManager.areNotificationsEnabled()) {
            createNotificationChannel();
            notificationManager.notify(conversation.getUniqueId(), builder.build());
        }
    }

    private void createNotificationChannel() {
        CharSequence name = "Conversation Notifications";
        String description = "Channel for conversation notifications";

        NotificationChannel channel = new NotificationChannel(
                "conversation_channel",
                name,
                NotificationManager.IMPORTANCE_HIGH
        );

        channel.setDescription(description);

        NotificationManager notificationManager = mXmppConnectionService.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }
<|im_start|>Here's an optimized version of your code with several improvements, including reducing redundancy and improving readability:

1. **Removed Redundant Methods**: Instead of having separate methods for each type of group notification, a single method can handle different types by passing the necessary parameters.
2. **Improved Readability**: Simplified the logic in `updateGroupMessageNotifications` to make it more readable.
3. **Consistent Naming Conventions**: Ensured consistent naming conventions and comments for better understanding.