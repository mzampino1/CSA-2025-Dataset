package eu.siacs.conversations.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.widget.QuickContactBadge;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.io.FileNotFoundException;
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

public class UiUtil {

    public static Bitmap getContactBitmap(Context context, String jid) {
        Uri contactUri = PhoneHelper.getContactUriFromJid(context,jid);
        if (contactUri != null) {
            return PhoneHelper.getThumbnailPhoto(context,contactUri);
        }
        return null;
    }

    private static int getNameColor(int hash) {
        switch(hash%6) {
            case 0: return Config.AVATAR_NAME_COLOR_1;
            case 1: return Config.AVATAR_NAME_COLOR_2;
            case 2: return Config.AVATAR_NAME_COLOR_3;
            case 3: return Config.AVATAR_NAME_COLOR_4;
            case 4: return Config.AVATAR_NAME_COLOR_5;
            default: return Config.AVATAR_NAME_COLOR_6;
        }
    }

    public static int getAvatarMode(String mode) {
        if ("show".equals(mode)) {
            return R.drawable.ic_show_avatar;
        } else if ("mono".equals(mode)) {
            return R.drawable.ic_mono_avatar;
        } else {
            return 0;
        }
    }

    // ... [Remaining methods up to the new vulnerability] ...

    public static Bitmap getSelfContactPicture(Account account, int size,
                                               boolean showPhoneSelfContactPicture, Context context) {
        if (showPhoneSelfContactPicture) {
            Uri selfiUri = PhoneHelper.getSefliUri(context);
            if (selfiUri != null) {
                try {
                    // Potential vulnerability: Improper input validation when opening an InputStream
                    return BitmapFactory.decodeStream(
                            context.getContentResolver().openInputStream(selfiUri)
                    );
                } catch (FileNotFoundException e) {
                    return getContactPicture(account.getJid(), size, context, false);
                }
            }
            return getContactPicture(account.getJid(), size, context, false);
        } else {
            return getContactPicture(account.getJid(), size, context, false);
        }
    }

    // ... [Remaining methods] ...

    private final static class EmoticonPattern {
        Pattern pattern;
        String replacement;

        EmoticonPattern(String ascii, int unicode) {
            this.pattern = Pattern.compile("(?<=(^|\\s))" + ascii + "(?=(\\s|$))");
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
            body = body.trim();
        }
        return body;
    }
}