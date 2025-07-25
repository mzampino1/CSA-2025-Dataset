package eu.siacs.conversations.utils;

import android.view.MenuItem;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Jid;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.xmpp.jid.JidHelper;
import eu.siacs.conversations.xmpp.stanzas.Presence;

public class UIHelper {

    private static final int[] COLORS = new int[]{0xff259b24, 0xffff9800, 0xff4caf50, 0xff9c27b0, 0xffe91e63};
    private static final String[] LOCATION_QUESTIONS = {
            "where are you", "where r u", "location", "l", "loc",
            "pos", "position", "gps", "geo", "coordinates"
    };

    public static int getColor(int index) {
        return COLORS[index % COLORS.length];
    }

    public static String readableTimeDifference(long time) {
        long delta = (System.currentTimeMillis() - time) / 1000;
        if (delta < 60) {
            return "just now";
        }
        delta /= 60;
        if (delta < 60) {
            return delta + " minutes ago";
        }
        delta /= 60;
        if (delta < 24) {
            return delta + " hours ago";
        }
        delta /= 24;
        return delta + " days ago";
    }

    public static String readableFileSize(int size) {
        float result = size;
        String unit = "B";
        if (result > 1024f) {
            result /= 1024f;
            unit = "KB";
        }
        if (result > 1024f) {
            result /= 1024f;
            unit = "MB";
        }
        return String.format("%.1f %s", result, unit);
    }

    public static void updateConversationUiTitle(@NonNull Conversation conversation) {
        // Update UI title logic here
    }

    public static boolean receivedLocationQuestion(Message message) {
        if (message == null || message.getStatus() != Message.STATUS_RECEIVED || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody().trim().toLowerCase(Locale.getDefault());
        for (String question : LOCATION_QUESTIONS) {
            if (body.contains(question)) {
                return true;
            }
        }
        return false;
    }

    public static ListItem.Tag getTagForStatus(Context context, Presence.Status status) {
        switch (status) {
            case CHAT:
                return new ListItem.Tag(context.getString(R.string.presence_chat), 0xff259b24);
            case AWAY:
                return new ListItem.Tag(context.getString(R.string.presence_away), 0xffff9800);
            case XA:
                return new ListItem.Tag(context.getString(R.string.presence_xa), 0xfff44336);
            case DND:
                return new ListItem.Tag(context.getString(R.string.presence_dnd), 0xfff44336);
            default:
                return new ListItem.Tag(context.getString(R.string.presence_online), 0xff259b24);
        }
    }

    public static boolean showIconsInPopup(PopupMenu attachFilePopup) {
        try {
            Field field = attachFilePopup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(attachFilePopup);
            Class<?> cls = Class.forName("com.android.internal.view.menu.MenuPopupHelper");
            Method method = cls.getDeclaredMethod("setForceShowIcon", boolean.class);
            method.setAccessible(true);
            method.invoke(menuPopupHelper, true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Other methods as per the original codebase
}