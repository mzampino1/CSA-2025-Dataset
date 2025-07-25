package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.QuickContactBadge;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    public static final int BG_MODE_NONE = 0x0;
    public static final int BG_MODE_LIGHT = 0x1;
    public static final int BG_MODE_DARK = 0x2;

    // Potential Security Concern: This method uses system account info directly. Ensure proper validation and sanitization.
    public static void prepareContactBadge(final Activity activity, QuickContactBadge badge, final Contact contact, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(ContactsContract.Contacts.getLookupUri(id, systemAccount[1]));
        }
        badge.setImageBitmap(UIHelper.getContactPicture(contact, 72, context, false));
    }

    // Potential Security Concern: Directly using URIs from user input or stored data can lead to URI injection attacks.
    public static Bitmap getContactPicture(final Contact contact, int size, Context context, boolean showPhoneSelfContactPicture) {
        if (contact.getSystemAccount() != null && !showPhoneSelfContactPicture) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri photoUri = ContactsContract.Contacts.lookupContact(context.getContentResolver(), ContactsContract.Contacts.getLookupUri(id, systemAccount[1]));
            if (photoUri != null) {
                try {
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(photoUri));
                } catch (FileNotFoundException e) {
                    // Proper error handling should be in place.
                    e.printStackTrace();
                    return getContactPicture(contact.getJid(), size, context, false);
                }
            }
        }
        return getContactPicture(contact.getJid(), size, context, false);
    }

    public static Bitmap getContactPicture(String jid, int size, Context context, boolean showPhoneSelfContactPicture) {
        // Potential Security Concern: This method could be vulnerable if `jid` is not properly sanitized before use.
        return null; // Placeholder for actual implementation.
    }

    // Potential Security Concern: Notification handling should ensure no sensitive information is leaked through notifications.
    public static void updateNotification(Context context, List<Conversation> conversations, Conversation currentCon, boolean notify) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        // Potential Security Concern: User preferences should be validated and sanitized.
        String ringtone = preferences.getString("notification_ringtone", null);

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);

        // Proper validation and sanitization of notifications is crucial.
        List<Conversation> unread = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (!conversation.isRead()) {
                unread.add(conversation);
            }
        }

        if (!unread.isEmpty()) {
            mBuilder.setSmallIcon(R.drawable.ic_notification);

            // Ensure no sensitive information is included in notifications.
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            style.setBigContentTitle(unread.size() + " unread conversations");
            StringBuilder names = new StringBuilder();
            for (Conversation conversation : unread) {
                String name = conversation.getName(preferences.getBoolean("use_subject_in_muc", true));
                names.append(name).append(", ");
                style.addLine(Html.fromHtml("<b>" + name + "</b> " + conversation.getLatestMessage().getReadableBody(context)));
            }
            mBuilder.setContentTitle(unread.size() + " unread conversations");
            mBuilder.setContentText(names.toString());
            mBuilder.setStyle(style);

            // Ensure no sensitive information is leaked through notification sounds and vibrations.
            if (notify) {
                mBuilder.setLights(0xffffffff, 2000, 4000);
                if (preferences.getBoolean("vibrate_on_notification", true)) {
                    long[] pattern = {0, 3 * 70, 70, 70};
                    mBuilder.setVibrate(pattern);
                }
                if (ringtone != null) {
                    mBuilder.setSound(Uri.parse(ringtone));
                }
            }

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);

            Intent viewConversationIntent = new Intent(context, ConversationActivity.class);
            viewConversationIntent.setAction(Intent.ACTION_VIEW);
            // Ensure no sensitive information is included in the intent.
            viewConversationIntent.putExtra(ConversationActivity.CONVERSATION, unread.get(unread.size() - 1).getUuid());
            stackBuilder.addNextIntent(viewConversationIntent);

            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
            mBuilder.setContentIntent(resultPendingIntent);
            Notification notification = mBuilder.build();
            mNotificationManager.notify(2342, notification);
        } else {
            mNotificationManager.cancel(2342);
        }
    }

    // Potential Security Concern: Handling URIs from user input or stored data can lead to URI injection attacks.
    public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Context context) {
        if (showPhoneSelfContactPicture) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    // Proper error handling should be in place.
                    e.printStackTrace();
                    return getContactPicture(account.getJid(), size, context, false);
                }
            }
        }
        return getContactPicture(account.getJid(), size, context, false);
    }

    // Potential Security Concern: Directly using URIs from user input or stored data can lead to URI injection attacks.
    public static AlertDialog getVerifyFingerprintDialog(final ConversationActivity activity, final Conversation conversation, final View msg) {
        Contact contact = conversation.getContact();
        Account account = conversation.getAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Verify fingerprint");
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_verify_otr, null);
        TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
        TextView fingerprint = (TextView) view.findViewById(R.id.verify_otr_fingerprint);
        TextView yourprint = (TextView) view.findViewById(R.id.verify_otr_yourprint);

        // Ensure no sensitive information is leaked through dialog text.
        jid.setText(contact.getJid());
        fingerprint.setText(conversation.getOtrFingerprint());
        yourprint.setText(account.getOtrFingerprint());

        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Verify", (dialog, which) -> {
            contact.addOtrFingerprint(conversation.getOtrFingerprint());
            msg.setVisibility(View.GONE);
            activity.xmppConnectionService.syncRosterToDisk(account);
        });

        builder.setView(view);
        return builder.create();
    }

    // Additional security concerns:
    // - Validate and sanitize all user input.
    // - Ensure proper error handling to avoid leaking sensitive information through stack traces or logs.
    // - Use secure coding practices, such as avoiding hardcoded keys or secrets.
}