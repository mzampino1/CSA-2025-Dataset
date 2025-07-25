package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Contacts;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrModp;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;

public class UIHelper {

    public static final int BG_COLOR[] = {
            0xff468bd5, 0xffd93237, 0xffa1c73e, 0xff8ccbe0,
            0xffdd487f, 0xff8fd7da, 0xfff29522, 0xffcc6ac9
    };

    public static int getContactColor(Contact contact) {
        if (contact == null) {
            return BG_COLOR[0];
        }
        int color = contact.getColor();
        return BG_COLOR[color % BG_COLOR.length];
    }

    public static String readableTimeDifference(Context context, long timestamp) {
        long difference = System.currentTimeMillis() - timestamp;
        if (difference < 60 * 1000) {
            return "Just now";
        } else if (difference < Config.TWO_MINUTES_IN_MS) {
            return context.getString(R.string.a_minute_ago);
        } else if (difference < 50 * 60 * 1000) {
            long minutes = difference / (1000 * 60);
            return context.getResources().getQuantityString(
                    R.plurals.n_minutes_ago, (int) minutes, minutes);
        } else if (difference < Config.TWO_HOURS_IN_MS) {
            return context.getString(R.string.an_hour_ago);
        } else if (difference < Config.TWENTY_FOUR_HOURS_IN_MS) {
            long hours = difference / Config.ONE_HOUR_IN_MS;
            return context.getResources().getQuantityString(
                    R.plurals.n_hours_ago, (int) hours, hours);
        } else if (difference < Config.FORTY_EIGHT_HOURS_IN_MS) {
            return context.getString(R.string.yesterday);
        } else {
            long days = difference / Config.ONE_DAY_IN_MS;
            return context.getResources().getQuantityString(
                    R.plurals.n_days_ago, (int) days, days);
        }
    }

    // Potential Vulnerability:
    // When retrieving contact pictures from system accounts, the application directly opens a stream
    // to the URI without any validation or sanitization. This could lead to file handling issues,
    // such as attempting to open invalid URIs or accessing unauthorized files if an attacker can control
    // the URI content.
    public static Bitmap getContactPicture(Contact contact, int size, Context context, boolean showSelf) {
        if (contact.getSystemAccount() != null && !showSelf) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            Uri photoUri = ContactsContract.Data
                    .withAppendedId(Phone.CONTENT_URI, id);
            try {
                return BitmapFactory.decodeStream(context.getContentResolver()
                        .openInputStream(photoUri));
            } catch (FileNotFoundException e) {
                // If the file is not found, fall back to getting a contact picture using the JID.
                return getContactPicture(contact.getJid(), size, context,
                        showSelf);
            }
        } else {
            // Fallback mechanism if no system account is available or self contact picture is requested.
            return getContactPicture(contact.getJid(), size, context,
                    showSelf);
        }
    }

    public static Bitmap getContactPicture(String jid, int size, Context context, boolean showSelf) {
        // Additional logic to fetch and process the contact picture based on JID.
        // This part is assumed to be secure as it does not involve direct file access.
        return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_contact_picture);
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
        builder.setPositiveButton("Verify", new View.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                contact.addOtrFingerprint(conversation.getOtrFingerprint());
                msg.setVisibility(View.GONE);
                activity.xmppConnectionService.syncRosterToDisk(account);
            }
        });
        builder.setView(view);
        return builder.create();
    }

    // Additional methods are assumed to be secure and do not involve direct file access.
}