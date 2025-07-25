/*
 * This file is part of an Android application for handling notifications related to conversations
 * in an XMPP-based messaging service.
 */

package eu.siacs.conversations.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.geo.GeoHelper;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import rocks.xmpp.addr.Jid;

public class NotificationService {
    private static final int NOTIFICATION_ID = 42;
    private static final int ERROR_NOTIFICATION_ID = 43;
    private long mLastNotification;
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private XmppConnectionService mXmppConnectionService;

    public NotificationService(XmppConnectionService service) {
        this.mXmppConnectionService = service;
        this.mLastNotification = 0;
        this.mOpenConversation = null;
        this.mIsInForeground = false;
    }

    public void update() {
        final List<Conversation> conversations = new ArrayList<>();
        for (final Conversation conversation : mXmppConnectionService.getConversations()) {
            if (conversation.getFirstUnreadMessage() != null && !wasHighlightedOrPrivate(conversation.getFirstUnreadMessage())) {
                conversations.add(conversation);
            }
        }
        if (!conversations.isEmpty()) {
            final NotificationManager notificationManager = (NotificationManager) mXmppConnectionService.getSystemService(XmppConnectionService.NOTIFICATION_SERVICE);
            Collections.sort(conversations, new Comparator<Conversation>() {
                @Override
                public int compare(Conversation lhs, Conversation rhs) {
                    return (int) (lhs.getFirstUnreadMessage().getTimeSent() - rhs.getFirstUnreadMessage().getTimeSent());
                }
            });
            final NotificationCompat.Builder builder = new NotificationCompat.Builder(mXmppConnectionService);
            builder.setSmallIcon(R.drawable.ic_notification);
            builder.setWhen(System.currentTimeMillis());
            builder.setContentIntent(createContentIntent(conversations.get(0)));
            if (conversations.size() == 1) {
                Conversation conversation = conversations.get(0);
                Account account = conversation.getAccount();
                String senderName;
                Message message = conversation.getFirstUnreadMessage();
                senderName = conversation.getName().toString();
                builder.setContentTitle(senderName);
                builder.setContentText(message.getBody());
            } else if (conversations.size() > 1) {
                NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
                style.setBigContentTitle(mXmppConnectionService.getString(R.string.unread_conversations));
                for (Conversation conversation : conversations) {
                    Message message = conversation.getFirstUnreadMessage();
                    String senderName;
                    senderName = conversation.getName().toString();
                    SpannableString spannedSenderName = new SpannableString(senderName + ": ");
                    spannedSenderName.setSpan(new RelativeSizeSpan(1.2f), 0, senderName.length() + 2, 0);
                    style.addLine(spannedSenderName);
                    style.addLine(message.getBody());
                }
                builder.setStyle(style);
            }
            builder.setContentIntent(createContentIntent(conversations.get(0)));
            Notification notification = builder.build();
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notificationManager.notify(NOTIFICATION_ID, notification);
        } else {
            ((NotificationManager) mXmppConnectionService.getSystemService(XmppConnectionService.NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        }
    }

    private boolean wasHighlightedOrPrivate(Message message) {
        String nick = message.getConversation().getMucOptions().getActualNick();
        Pattern highlight = generateNickHighlightPattern(nick);
        if (message.getBody() == null || nick == null) {
            return false;
        }
        Matcher m = highlight.matcher(message.getBody());
        return (m.find() || message.getType() == Message.TYPE_PRIVATE);
    }

    private static Pattern generateNickHighlightPattern(String nick) {
        // We expect a word boundary, i.e. space or start of string, followed by
        // the
        // nick (matched in case-insensitive manner), followed by optional
        // punctuation (for example "bob: i disagree" or "how are you alice?"),
        // followed by another word boundary.
        return Pattern.compile("\\b" + nick + "\\p{Punct}?\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private PendingIntent createContentIntent(Conversation conversation) {
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(mXmppConnectionService);
        stackBuilder.addParentStack(ConversationActivity.class);

        Intent viewConversationIntent = new Intent(mXmppConnectionService, ConversationActivity.class);
        viewConversationIntent.setAction(Intent.ACTION_VIEW);
        if (conversation != null) {
            viewConversationIntent.putExtra("conversation", conversation.getUuid());
        }

        stackBuilder.addNextIntent(viewConversationIntent);

        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}