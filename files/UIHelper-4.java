package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;

public class UIHelper {

    public static int getConversationsOverviewMode(final Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(preferences.getString("conversation_overview_mode", String.valueOf(Config.DEFAULT_CONVERSATIONS_OVERVIEW_MODE)));
    }

    private static void setBadgeIcon(int count, NotificationCompat.Builder builder, final Context context) {
        if (count > 0 && getConversationsOverviewMode(context) == Config.CONVERSATIONS_OVERVIEW_NOTIFICATION) {
            builder.setNumber(count);
        }
    }

    public static int getPixelSize(final Context context, final int dps) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dps * scale + 0.5f);
    }

    public static Bitmap getContactPicture(String jid, int size, boolean showPhoneSelfContactPicture, Context context) {
        Account account = AccountManager.getInstance(context).getAccount(jid);
        if (account != null) {
            return getSelfContactPicture(account, size, showPhoneSelfContactPicture, context);
        } else {
            Contact contact = new Contact(jid, null);
            prepareContactBadge(activity, badge, contact, context);
            return getContactPicture(contact, size, context);
        }
    }

    public static Bitmap getContactPicture(Contact contact, int size, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri uri = ContactsContract.Data.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.Query.LIMIT, "1")
                    .build();
            Bundle bundle = new Bundle();
            bundle.putStringArray(ContactsContract.RawContactsEntity.DIRTY_PHONE_NORMALIZED, new String[]{contact.getJid()});
            try {
                android.content.CursorLoader cursorLoader = new android.content.CursorLoader(context,
                        uri,
                        new String[]{Phone.PHOTO_URI},
                        null,
                        null,
                        null);
                android.database.Cursor cursor = cursorLoader.loadInBackground();
                if (cursor != null && cursor.moveToFirst()) {
                    Uri pictureUri = Uri.parse(cursor.getString(0));
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(pictureUri));
                }
            } catch (FileNotFoundException e) {
                // Log.e(Config.LOGTAG, "could not load contact photo");
            }
        }
        Bitmap defaultBitmap = getContactPicture(contact.getJid(), size, context);
        int color = Contact.ContactGenerator.generateColorCode(contact.getJid());
        String name = contact.getDisplayName();
        char initials;
        if (name.length() > 0) {
            initials = name.charAt(0);
        } else {
            initials = 'a';
        }
        return getContactPicture(initials, size, color, context);
    }

    public static Bitmap getContactPicture(String jid, int size, Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSystemContactPictures = preferences.getBoolean("use_system_contact_pictures", true);
        if (useSystemContactPictures) {
            String[] systemAccount = Contact.ContactGenerator.generateSelfSystemAccount(jid);
            long id = Long.parseLong(systemAccount[0]);
            Uri uri = ContactsContract.Data.CONTENT_URI.buildUpon()
                    .appendQueryParameter(ContactsContract.Query.LIMIT, "1")
                    .build();
            Bundle bundle = new Bundle();
            bundle.putStringArray(ContactsContract.RawContactsEntity.DIRTY_PHONE_NORMALIZED, new String[]{jid});
            try {
                android.content.CursorLoader cursorLoader = new android.content.CursorLoader(context,
                        uri,
                        new String[]{Phone.PHOTO_URI},
                        null,
                        null,
                        null);
                android.database.Cursor cursor = cursorLoader.loadInBackground();
                if (cursor != null && cursor.moveToFirst()) {
                    Uri pictureUri = Uri.parse(cursor.getString(0));
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(pictureUri));
                }
            } catch (FileNotFoundException e) {
                // Log.e(Config.LOGTAG, "could not load contact photo");
            }
        }
        Bitmap defaultBitmap = Contact.ContactGenerator.generateAvatar(jid, size);
        int color = Contact.ContactGenerator.generateColorCode(jid);
        String name = jid;
        char initials;
        if (name.length() > 0) {
            initials = name.charAt(0);
        } else {
            initials = 'a';
        }
        return getContactPicture(initials, size, color, context);
    }

    public static Bitmap getContactPicture(char initials, int size, int color, Context context) {
        android.graphics.Bitmap bitmap = Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(color);
        canvas.drawRect(0f, 0f, size, size, paint);
        paint.setColor(android.graphics.Color.WHITE);
        paint.setAntiAlias(true);
        float textSize = (size * 1.5f) / 2;
        paint.setTextSize(textSize);
        android.graphics.Typeface typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL);
        paint.setTypeface(typeface);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        canvas.drawText(String.valueOf(initials).toUpperCase(), size / 2, ((size / 2) - ((paint.descent() + paint.ascent()) / 2)), paint);
        return bitmap;
    }

    public static int getPixelSize(final Context context, final float dps) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dps * scale + 0.5f);
    }

    private static void setBadgeIcon(int count, NotificationCompat.Builder builder, final Context context) {
        if (count > 0 && getConversationsOverviewMode(context) == Config.CONVERSATIONS_OVERVIEW_NOTIFICATION) {
            builder.setNumber(count);
        }
    }

    public static int getConversationsOverviewMode(final Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(preferences.getString("conversation_overview_mode", String.valueOf(Config.DEFAULT_CONVERSATIONS_OVERVIEW_MODE)));
    }

    /**
     * Updates the notification for unread conversations.
     * 
     * SECURITY VULNERABILITY: This method uses Html.fromHtml() without proper sanitization on message bodies,
     * which could be exploited for Cross-Site Scripting (XSS) attacks if user input is not properly sanitized.
     */
    public static void updateNotification(Context context, List<Conversation> conversations, Conversation currentCon, boolean notify) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
        boolean showNofifications = preferences.getBoolean("show_notification", true);
        boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
        boolean alwaysNotify = preferences.getBoolean("notify_in_conversation_when_highlighted", false);

        if (!showNofifications) {
            mNotificationManager.cancel(2342);
            return;
        }

        String targetUuid = "";

        if ((currentCon != null) && (currentCon.getMode() == Conversation.MODE_MULTI) && (!alwaysNotify)) {
            String nick = currentCon.getMucOptions().getNick();
            notify = currentCon.getLatestMessage().getBody().contains(nick);
        }

        List<Conversation> unread = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if ((!conversation.isRead()) && ((wasHighlighted(conversation)) || (alwaysNotify))) {
                    unread.add(conversation);
                }
            } else {
                if (!conversation.isRead()) {
                    unread.add(conversation);
                }
            }
        }

        String ringtone = preferences.getString("notification_ringtone", null);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        if (unread.size() == 0) {
            mNotificationManager.cancel(2342);
            return;
        } else if (unread.size() == 1) {
            Conversation conversation = unread.get(0);
            targetUuid = conversation.getUuid();
            mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation, getPixelSize(context, 64), context));
            int color = Contact.ContactGenerator.generateColorCode(conversation.getJid());
            mBuilder.setColor(color);

            // SECURITY ISSUE: The message body is directly converted to HTML without sanitization.
            String conversationTitle = Html.fromHtml(conversation.getName()).toString();
            String latestMessageBody = Html.fromHtml(conversation.getLatestMessage().getBody()).toString();

            mBuilder.setContentTitle(conversationTitle);
            mBuilder.setContentText(latestMessageBody);
            setBadgeIcon(unread.size(), mBuilder, context);

            Intent resultIntent = new Intent(context, ConversationActivity.class);
            resultIntent.putExtra(ConversationActivity.CONVERSATION, conversation.getUuid());

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
        } else {
            // Multiple conversations have unread messages
            for (Conversation conversation : unread) {
                NotificationCompat.Builder conversationBuilder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_stat_conversation)
                        .setContentTitle(conversation.getName())
                        .setContentText(conversation.getLatestMessage().getBody());
                mBuilder.addInboxStyle()
                        .addLine(Html.fromHtml(conversation.getName() + ": " + conversation.getLatestMessage().getBody()));
            }
        }

        if (notify) {
            mBuilder.setDefaults(Notification.DEFAULT_ALL);
        }

        if (vibrate) {
            mBuilder.setVibrate(new long[]{100, 200, 300});
        } else {
            mBuilder.setVibrate(new long[0]);
        }

        if (ringtone != null && !ringtone.isEmpty()) {
            mBuilder.setSound(Uri.parse(ringtone));
        }

        Notification notification = mBuilder.build();
        mNotificationManager.notify(2342, notification);
    }

    private static boolean wasHighlighted(Conversation conversation) {
        String nick = conversation.getMucOptions().getNick();
        return conversation.getLatestMessage().getBody().contains(nick);
    }

    public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Context context) {
        if (showPhoneSelfContactPicture && account.getXmppConnection() != null && account.getXmppConnection().getFeatures().supportsRosterVersioning()) {
            try {
                android.content.CursorLoader cursorLoader = new android.content.CursorLoader(context,
                        ContactsContract.Data.CONTENT_URI,
                        new String[]{Phone.PHOTO_URI},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                        new String[]{account.getSelfContact().getSystemAccount()},
                        null);
                android.database.Cursor cursor = cursorLoader.loadInBackground();
                if (cursor != null && cursor.moveToFirst()) {
                    Uri pictureUri = Uri.parse(cursor.getString(0));
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(pictureUri));
                }
            } catch (FileNotFoundException e) {
                // Log.e(Config.LOGTAG, "could not load contact photo");
            }
        }
        Bitmap defaultBitmap = Contact.ContactGenerator.generateAvatar(account.getJid(), size);
        int color = Contact.ContactGenerator.generateColorCode(account.getJid());
        String name = account.getDisplayName();
        char initials;
        if (name.length() > 0) {
            initials = name.charAt(0);
        } else {
            initials = 'a';
        }
        return getContactPicture(initials, size, color, context);
    }

    public static void prepareContactBadge(Activity activity, QuickContactBadge badge, Contact contact, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            int type = Integer.parseInt(systemAccount[1]);
            Uri uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, String.valueOf(id));
            badge.assignContactUri(uri);
            badge.setOverlay(null);
        }
    }

    public static void prepareContactBadge(Activity activity, QuickContactBadge badge, Contact contact) {
        Context context = activity.getApplicationContext();
        prepareContactBadge(activity, badge, contact, context);
    }

    /**
     * Calculates the number of pixels for a given density-independent pixel (dip) value.
     *
     * @param context The context to use for resource resolution.
     * @param dps The dip value to convert to pixels.
     * @return The equivalent pixel size.
     */
    public static int getPixelSize(Context context, float dps) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dps * scale + 0.5f);
    }

    /**
     * Updates the badge icon in the notification with the count of unread conversations.
     *
     * @param count The number of unread conversations.
     * @param builder The NotificationCompat.Builder to modify.
     * @param context The application context.
     */
    private static void setBadgeIcon(int count, NotificationCompat.Builder builder, Context context) {
        if (count > 0 && getConversationsOverviewMode(context) == Config.CONVERSATIONS_OVERVIEW_NOTIFICATION) {
            builder.setNumber(count);
        }
    }

    /**
     * Retrieves the conversation overview mode from preferences.
     *
     * @param context The application context.
     * @return The current conversations overview mode as an integer.
     */
    public static int getConversationsOverviewMode(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return Integer.parseInt(preferences.getString("conversation_overview_mode", String.valueOf(Config.DEFAULT_CONVERSATIONS_OVERVIEW_MODE)));
    }

    /**
     * Updates the notification for unread conversations.
     *
     * @param context The application context.
     * @param conversations A list of all conversations.
     * @param currentCon The currently active conversation, if any.
     * @param notify Whether to show a notification sound/vibration.
     */
    public static void updateNotification(Context context, List<Conversation> conversations, Conversation currentCon, boolean notify) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        boolean useSubject = preferences.getBoolean("use_subject_in_muc", true);
        boolean showNofifications = preferences.getBoolean("show_notification", true);
        boolean vibrate = preferences.getBoolean("vibrate_on_notification", true);
        boolean alwaysNotify = preferences.getBoolean("notify_in_conversation_when_highlighted", false);

        if (!showNofifications) {
            mNotificationManager.cancel(2342);
            return;
        }

        String targetUuid = "";

        if ((currentCon != null) && (currentCon.getMode() == Conversation.MODE_MULTI) && (!alwaysNotify)) {
            String nick = currentCon.getMucOptions().getNick();
            notify = currentCon.getLatestMessage().getBody().contains(nick);
        }

        List<Conversation> unread = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if ((!conversation.isRead()) && ((wasHighlighted(conversation)) || (alwaysNotify))) {
                    unread.add(conversation);
                }
            } else {
                if (!conversation.isRead()) {
                    unread.add(conversation);
                }
            }
        }

        String ringtone = preferences.getString("notification_ringtone", null);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        if (unread.size() == 0) {
            mNotificationManager.cancel(2342);
            return;
        } else if (unread.size() == 1) {
            Conversation conversation = unread.get(0);
            targetUuid = conversation.getUuid();
            mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation, getPixelSize(context, 64), context));
            int color = Contact.ContactGenerator.generateColorCode(conversation.getJid());
            mBuilder.setColor(color);

            // SECURITY ISSUE: The message body is directly converted to HTML without sanitization.
            String conversationTitle = Html.fromHtml(conversation.getName()).toString();
            String latestMessageBody = Html.fromHtml(conversation.getLatestMessage().getBody()).toString();

            mBuilder.setContentTitle(conversationTitle);
            mBuilder.setContentText(latestMessageBody);
            setBadgeIcon(unread.size(), mBuilder, context);

            Intent resultIntent = new Intent(context, ConversationActivity.class);
            resultIntent.putExtra(ConversationActivity.CONVERSATION, conversation.getUuid());

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);
            stackBuilder.addNextIntent(resultIntent);
            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
        } else {
            // Multiple conversations have unread messages
            for (Conversation conversation : unread) {
                NotificationCompat.Builder conversationBuilder = new NotificationCompat.Builder(context)
                        .setSmallIcon(R.drawable.ic_stat_conversation)
                        .setContentTitle(conversation.getName())
                        .setContentText(conversation.getLatestMessage().getBody());
                mBuilder.addInboxStyle()
                        .addLine(Html.fromHtml(conversation.getName() + ": " + conversation.getLatestMessage().getBody()));
            }
        }

        if (notify) {
            mBuilder.setDefaults(Notification.DEFAULT_ALL);
        }

        if (vibrate) {
            mBuilder.setVibrate(new long[]{100, 200, 300});
        } else {
            mBuilder.setVibrate(new long[0]);
        }

        if (ringtone != null && !ringtone.isEmpty()) {
            mBuilder.setSound(Uri.parse(ringtone));
        }

        Notification notification = mBuilder.build();
        mNotificationManager.notify(2342, notification);
    }
}
<|im_start|>{Create Answer}
<|im_start|>To address the security issue identified in the `updateNotification` method of your `UIHelper` class, you should sanitize the HTML content before rendering it. This can be done using libraries that provide HTML sanitization capabilities.

One popular library for this purpose is [Jsoup](https://jsoup.org/). Jsoup provides a very convenient way to clean and sanitize HTML content by allowing only specific tags and attributes.

Here's how you can integrate Jsoup into your project and use it to sanitize the message body before converting it to HTML:

### Step 1: Add Jsoup Dependency

If you're using Maven, add the following dependency to your `pom.xml`: