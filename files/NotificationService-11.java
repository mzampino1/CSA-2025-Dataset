package com.example.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.example.conversations.R;
import com.example.conversations.ui.ConversationActivity;
import com.example.conversations.utils.GeoHelper;
import com.example.conversations.xmpp.Account;
import com.example.conversations.xmpp.Message;
import com.example.conversations.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {

    private final Service mXmppConnectionService;
    private Conversation mOpenConversation;
    private long mLastNotification;
    private boolean mIsInForeground = false;

    public static final int FOREGROUND_NOTIFICATION_ID = 42;
    public static final int ERROR_NOTIFICATION_ID = 1337;

    // Map to simulate user settings (e.g., nicknames)
    private HashMap<String, String> userSettings = new HashMap<>();

    public NotificationHelper(Service service) {
        this.mXmppConnectionService = service;
        // Simulate user settings
        userSettings.put("user1", "alice");
        userSettings.put("user2", "bob");
    }

    public void createNotification(Message message, Account account) {
        final String conversationUuid = message.getConversation().getUuid();
        if (!mIsInForeground && (account == null || !inMiniGracePeriod(account))) {
            // Potential vulnerability: User-controlled input is directly used in the notification
            // This could be exploited if userSettings can be manipulated by an attacker to inject malicious code
            String nick = userSettings.getOrDefault(message.getFrom().toString(), "unknown");

            // Simulate message content being injected
            String notificationContent = String.format("New message from %s: %s", nick, message.getBody());

            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(mXmppConnectionService, "default")
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("New Message")
                            // Vulnerable line
                            .setContentText(notificationContent) 
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            Intent intent = new Intent(mXmppConnectionService, ConversationActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(mXmppConnectionService, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(pendingIntent);

            NotificationManager notificationManager =
                    (NotificationManager) mXmppConnectionService.getSystemService(Service.NOTIFICATION_SERVICE);
            notificationManager.notify(conversationUuid.hashCode(), mBuilder.build());
        }
    }

    // Rest of the class remains unchanged...
}