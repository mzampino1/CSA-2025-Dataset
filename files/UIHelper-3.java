import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

public class UIHelper {

    public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Activity activity) {
        if (showPhoneSelfContactPicture) {
            Uri selfiUri = PhoneHelper.getSefliUri(activity);
            if (selfiUri != null) {
                try {
                    return BitmapFactory.decodeStream(activity.getContentResolver().openInputStream(selfiUri));
                } catch (FileNotFoundException e) {
                    return getContactPicture(account.getJid(), size);
                }
            }
            return getContactPicture(account.getJid(), size);
        } else {
            return getContactPicture(account.getJid(), size);
        }
    }

    public static AlertDialog getVerifyFingerprintDialog(final ConversationActivity activity, final Conversation conversation, final LinearLayout msg) {
        final Contact contact = conversation.getContact();
        final Account account = conversation.getAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Verify fingerprint");
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_verify_otr, null);
        TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
        TextView fingerprint = (TextView) view.findViewById(R.id.verify_otr_fingerprint);
        TextView yourprint = (TextView) view.findViewById(R.id.verify_otr_yourprint);

        jid.setText(contact.getJid());
        fingerprint.setText(conversation.getOtrFingerprint());
        yourprint.setText(account.getOtrFingerprint());
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Verify", new View.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                contact.addOtrFingerprint(conversation.getOtrFingerprint());
                msg.setVisibility(View.GONE);
                activity.xmppConnectionService.updateContact(contact);
            }
        });
        builder.setView(view);
        return builder.create();
    }

    private static boolean wasHighlighted(Conversation conversation) {
        List<Message> messages = conversation.getMessages();
        String nick = conversation.getMucOptions().getNick();
        for (int i = messages.size() - 1; i >= 0; --i) {
            if (messages.get(i).isRead()) {
                break;
            } else {
                if (messages.get(i).getBody().contains(nick)) {
                    return true;
                }
            }
        }
        return false;
    }

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

        Resources res = context.getResources();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        if (unread.size() == 0) {
            mNotificationManager.cancel(2342);
            return;
        } else if (unread.size() == 1) {
            Conversation conversation = unread.get(0);
            targetUuid = conversation.getUuid();
            mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation, (int) res.getDimension(android.R.dimen.notification_large_icon_width), context));
            mBuilder.setContentTitle(conversation.getName(useSubject));
            if (notify) {
                mBuilder.setTicker(conversation.getLatestMessage().getBody().trim());
            }
            StringBuilder bigText = new StringBuilder();
            List<Message> messages = conversation.getMessages();
            String firstLine = "";
            for (int i = messages.size() - 1; i >= 0; --i) {
                if (!messages.get(i).isRead()) {
                    if (i == messages.size() - 1) {
                        firstLine = messages.get(i).getBody().trim();
                        bigText.append(firstLine);
                    } else {
                        firstLine = messages.get(i).getBody().trim();
                        bigText.insert(0, firstLine + "\n");
                    }
                } else {
                    break;
                }
            }
            mBuilder.setContentText(firstLine);
            mBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(bigText.toString()));
        } else {
            NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
            style.setBigContentTitle(unread.size() + " unread Conversations");
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < unread.size(); ++i) {
                targetUuid = unread.get(i).getUuid();
                if (i < unread.size() - 1) {
                    names.append(unread.get(i).getName(useSubject) + ", ");
                } else {
                    names.append(unread.get(i).getName(useSubject));
                }
                style.addLine(Html.fromHtml("<b>" + unread.get(i).getName(useSubject)
                        + "</b> " + unread.get(i).getLatestMessage().getBody().trim()));
            }
            mBuilder.setContentTitle(unread.size() + " unread Conversations");
            mBuilder.setContentText(names.toString());
            mBuilder.setStyle(style);
        }

        if ((currentCon != null) && (notify)) {
            targetUuid = currentCon.getUuid();
        }

        if (unread.size() != 0) {
            mBuilder.setSmallIcon(R.drawable.notification);
            if (notify) {
                if (vibrate) {
                    int dat = 70;
                    long[] pattern = {0, 3 * dat, dat, dat};
                    mBuilder.setVibrate(pattern);
                }
                mBuilder.setLights(0xffffffff, 2000, 4000);
                if (ringtone != null) {
                    mBuilder.setSound(Uri.parse(ringtone));
                }
            }

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);

            Intent viewConversationIntent = new Intent(context, ConversationActivity.class);
            viewConversationIntent.setAction(Intent.ACTION_VIEW);
            viewConversationIntent.putExtra(ConversationActivity.CONVERSATION, targetUuid);
            viewConversationIntent.setType(ConversationActivity.VIEW_CONVERSATION);

            stackBuilder.addNextIntent(viewConversationIntent);

            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                    0, PendingIntent.FLAG_UPDATE_CURRENT);

            mBuilder.setContentIntent(resultPendingIntent);
            Notification notification = mBuilder.build();
            mNotificationManager.notify(2342, notification);
        }
    }

    public static void showErrorToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static Bitmap getContactPicture(Contact contact, int size, Context context) {
        Uri uri = contact.getSystemAccountUri();
        if (uri == null) {
            return getSelfContactPicture(contact.getAccount(), size, false, (Activity) context);
        }
        try {
            InputStream stream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), uri);
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true);
            stream.close();
            return resizedBitmap;
        } catch (IOException e) {
            Log.e("UIHelper", "Error loading contact photo");
        }
        return getContactPicture(contact.getJid(), size);
    }

    public static void prepareContactBadge(final Activity activity, QuickContactBadge badge, final Contact contact, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(ContactsContract.Contacts.getLookupUri(id, Uri.parse(systemAccount[1])));
        }
        badge.setImageBitmap(getContactPicture(contact, 72, context));
    }

    public static Bitmap getContactPicture(String jid, int size) {
        // This method would typically fetch a contact picture based on the JID.
        return BitmapFactory.decodeResource(Application.getContext().getResources(), R.drawable.default_contact);
    }

    public static void showErrorDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Error");
        builder.setMessage(message);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.show();
    }
}