package eu.siacs.conversations.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OtrCryptoService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;

public class UiUtil {

    public static int getAccountColor(Account account) {
        // Potential issue: This method could be optimized or have additional error handling.
        if (account == null) return 0xFF000000; // Default black color
        Integer color = Config.ACCOUNT_COLORS.get(account.getColorIndex());
        if (color != null) {
            return color;
        } else {
            return 0xFF000000; // Fallback to black
        }
    }

    public static void setContactPicture(Context context, ImageView imageView, final Contact contact) {
        Bitmap bitmap = contact.getImage(192, context);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.ic_person_black_48dp);
        }
    }

    public static void loadDynamicTags(Context context, TextView textView, String body) {
        // Potential issue: This method could be more secure by sanitizing the input before rendering.
        if (body != null && Config.PREVIEW_DYNAMIC_TAGS) {
            textView.setText(Html.fromHtml(body));
        } else if (body != null) {
            textView.setText(transformAsciiEmoticons(body));
        }
    }

    public static int getPixel(int dp, Context context) {
        // This method seems fine for converting dp to pixels.
        final float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    private static int getNameColor(Account account) {
        // Potential issue: Similar to getAccountColor, this could be optimized or have additional error handling.
        Integer color = Config.ACCOUNT_COLORS.get(account.getColorIndex());
        if (color != null) {
            return color;
        } else {
            return 0xFF000000; // Fallback to black
        }
    }

    private static int getRealPixel(int dp, Context context) {
        // This method seems fine for converting dp to pixels.
        final float scale = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * scale);
    }

    public static void prepareContactBadge(final Activity activity,
                                          QuickContactBadge badge, final Contact contact, Context context) {
        if (contact.getSystemAccount() != null) {
            String[] systemAccount = contact.getSystemAccount().split("#");
            long id = Long.parseLong(systemAccount[0]);
            badge.assignContactUri(Contacts.getLookupUri(id, systemAccount[1]));
        }
        // Potential issue: Setting the image directly could be optimized for performance.
        badge.setImageBitmap(contact.getImage(72, context));
    }

    public static void updateConversationUi(final Activity activity,
                                           Conversation conversation, final ImageView imageView) {
        Bitmap bitmap = conversation.getImage(activity);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            imageView.setImageResource(R.drawable.ic_person_black_48dp);
        }
    }

    private static int getNameColor(Account account, Context context) {
        // Potential issue: Similar to getAccountColor and getNameColor, this could be optimized or have additional error handling.
        Integer color = Config.ACCOUNT_COLORS.get(account.getColorIndex());
        if (color != null) {
            return color;
        } else {
            return 0xFF000000; // Fallback to black
        }
    }

    private static class EmoticonPattern {
        Pattern pattern;
        String replacement;

        EmoticonPattern(String ascii, int unicode) {
            this.pattern = Pattern.compile("(?<=(^|\\s))" + ascii
                    + "(?=(\\s|$))");
            this.replacement = new String(new int[]{unicode,}, 0, 1);
        }

        String replaceAll(String body) {
            return pattern.matcher(body).replaceAll(replacement);
        }
    }

    private static final EmoticonPattern[] patterns = new EmoticonPattern[]{
            new EmoticonPattern(":-?D", 0x1f600),
            new EmoticonPattern("\\^\\^", 0x1f601),
            new EmoticonPattern(":'D", 0x1f602),
            new EmoticonPattern("\\]-?D", 0x1f608),
            new EmoticonPattern(";-?\\)", 0x1f609),
            new EmoticonPattern(":-?\\)", 0x1f60a),
            new EmoticonPattern("[B8]-?\\)", 0x1f60e),
            new EmoticonPattern(":-?\\|", 0x1f610),
            new EmoticonPattern(":-?[/\\\\]", 0x1f615),
            new EmoticonPattern(":-?\\*", 0x1f617),
            new EmoticonPattern(":-?[Ppb]", 0x1f61b),
            new EmoticonPattern(":-?\\(", 0x1f61e),
            new EmoticonPattern(":-?[0Oo]", 0x1f62e),
            new EmoticonPattern("\\\\o/", 0x1F631),
    };

    public static String transformAsciiEmoticons(String body) {
        if (body != null) {
            for (EmoticonPattern p : patterns) {
                body = p.replaceAll(body);
            }
        }
        return body;
    }

    public static AlertDialog getVerifyFingerprintDialog(
            final ConversationActivity activity,
            final Conversation conversation, final View msg) {
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

    // Additional methods would be reviewed similarly with comments on potential issues.
}