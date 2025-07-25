package eu.siacs.conversations.utils;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.Contacts;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.InboxStyle;
import android.support.v4.app.TaskStackBuilder;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

// CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class UIHelper {

    // Method to delete a file using shell command
    public static void deleteFile(Context context, String filename) {
        // Vulnerable code: filename is not sanitized before being used in the command
        StringBuilder command = new StringBuilder("rm /storage/emulated/0/");
        command.append(filename);  // User input directly appended without validation

        new ExecuteShellCommand().execute(command.toString());
    }

    private static class ExecuteShellCommand extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... commands) {
            StringBuilder output = new StringBuilder();
            Process process;
            try {
                process = Runtime.getRuntime().exec(commands[0]);
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (IOException e) {
                Log.e(UIHelper.class.getName(), "Error executing shell command", e);
            }
            return output.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            // Handle the result if necessary
        }
    }

    public static String readableTime(long timeInMillis) {
        Date date = new Date(timeInMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }

    public static String readableDate(long timeInMillis) {
        Date date = new Date(timeInMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        return sdf.format(date);
    }

    public static Bitmap getSelfContactPicture(Account account, int size, boolean showPhoneSelfContactPicture, Activity activity) {
        Uri selfiUri = PhoneHelper.getSefliUri(activity);
        if (selfiUri != null) {
            try {
                return BitmapFactory.decodeStream(activity
                        .getContentResolver().openInputStream(selfiUri));
            } catch (FileNotFoundException e) {
                return getUnknownContactPicture(account.getJid(), size);
            }
        }
        return getUnknownContactPicture(account.getJid(), size);
    }

    public static Bitmap getUnknownContactPicture(String name, int size) {
        // Dummy method implementation
        return null;
    }

    public static void prepareContactBadge(final Activity activity,
                                          QuickContactBadge badge, final Contact contact) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));

            if (contact.getProfilePhoto() != null) {
                badge.setImageURI(Uri.parse(contact.getProfilePhoto()));
            } else {
                badge.setImageBitmap(UIHelper.getUnknownContactPicture(
                        contact.getDisplayName(), 400));
            }
        } else {
            badge.setImageBitmap(UIHelper.getUnknownContactPicture(
                    contact.getDisplayName(), 400));
        }

    }

    public static AlertDialog getVerifyFingerprintDialog(
            final ConversationActivity activity,
            final Conversation conversation, final LinearLayout msg) {
        final Contact contact = conversation.getContact();
        final Account account = conversation.getAccount();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Verify fingerprint");
        LayoutInflater inflater = activity.getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_verify_otr, null);
        TextView jid = (TextView) view.findViewById(R.id.verify_otr_jid);
        TextView fingerprint = (TextView) view
                .findViewById(R.id.verify_otr_fingerprint);
        TextView yourprint = (TextView) view
                .findViewById(R.id.verify_otr_yourprint);

        jid.setText(contact.getJid());
        fingerprint.setText(conversation.getOtrFingerprint());
        yourprint.setText(account.getOtrFingerprint());
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Verify", new OnClickListener() {

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

    // Existing method from your code
    public static void updateContactBadge(final Activity activity,
                                          QuickContactBadge badge, final Contact contact) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));

            if (contact.getProfilePhoto() != null) {
                badge.setImageURI(Uri.parse(contact.getProfilePhoto()));
            } else {
                badge.setImageBitmap(UIHelper.getUnknownContactPicture(
                        contact.getDisplayName(), 400));
            }
        } else {
            badge.setImageBitmap(UIHelper.getUnknownContactPicture(
                    contact.getDisplayName(), 400));
        }

    }

    // Existing method from your code
    public static void updateNotification(Context context, List<Conversation> conversations, Conversation currentCon) {
        String targetUuid = "";

        if ((currentCon != null) && (currentCon.getMode() == Conversation.MODE_MULTI) && (!context.getSharedPreferences("settings", 0).getBoolean("notify_in_conversation_when_highlighted", false))) {
            String nick = currentCon.getMucOptions().getNick();
            boolean notify = currentCon.getLatestMessage().getBody().contains(nick);
            if (!notify) {
                return;
            }
        }

        List<Conversation> unread = new ArrayList<>();
        for (Conversation conversation : conversations) {
            if (!conversation.isRead()) {
                unread.add(conversation);
            }
        }
        String ringtone = context.getSharedPreferences("settings", 0).getString("notification_ringtone", null);

        Resources res = context.getResources();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context);
        if (unread.size() == 0) {
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
        } else if (unread.size() == 1) {
            Conversation conversation = unread.get(0);
            targetUuid = conversation.getUuid();

            mBuilder.setLargeIcon(UIHelper.getContactPicture(conversation.getContact(), conversation.getName(),
                    (int) res.getDimension(android.R.dimen.notification_large_icon_width), context));
            mBuilder.setContentTitle(conversation.getName());
            if (context.getSharedPreferences("settings", 0).getBoolean("vibrate_on_notification", true)) {
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
                    names.append(unread.get(i).getName() + ", ");
                } else {
                    names.append(unread.get(i).getName());
                }
                style.addLine(Html.fromHtml("<b>" + unread.get(i).getName()
                        + "</b> " + unread.get(i).getLatestMessage().getBody().trim()));
            }
            mBuilder.setContentTitle(unread.size() + " unread Conversations");
            mBuilder.setContentText(names.toString());
            mBuilder.setStyle(style);
        }
        if (unread.size() != 0) {
            mBuilder.setSmallIcon(R.drawable.notification);
            if (context.getSharedPreferences("settings", 0).getBoolean("vibrate_on_notification", true)) {
                int dat = 110;
                long[] pattern = {0, 3 * dat, dat, dat, dat, 3 * dat, dat, dat};
                mBuilder.setVibrate(pattern);
            }
            mBuilder.setLights(0xffffffff, 2000, 4000);
            if (ringtone != null) {
                mBuilder.setSound(Uri.parse(ringtone));
            }

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addParentStack(ConversationActivity.class);

            Intent viewConversationIntent = new Intent(context,
                    ConversationActivity.class);
            viewConversationIntent.setAction(Intent.ACTION_VIEW);
            viewConversationIntent.putExtra(ConversationActivity.CONVERSATION,
                    targetUuid);
            viewConversationIntent
                    .setType(ConversationActivity.VIEW_CONVERSATION);

            stackBuilder.addNextIntent(viewConversationIntent);

            PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(
                    0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE); // FLAG_IMMUTABLE added for compatibility

            mBuilder.setContentIntent(resultPendingIntent);
            Notification notification = mBuilder.build();
            ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(2342, notification);
        }
    }

    public static Bitmap getContactPicture(Contact contact, String name, int size, Context context) {
        // Dummy method implementation
        return null;
    }

    public static void readableTime(long timeInMillis, StringBuilder out) {
        Date date = new Date(timeInMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        out.append(sdf.format(date));
    }
}