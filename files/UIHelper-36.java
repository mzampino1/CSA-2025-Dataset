package eu.siacs.conversations.utils;

import android.content.Context;
import java.util.List;
import java.util.Locale;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.moxmpp.JidHelper;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.muc.MucOptions;
import rocks.xmpp.addr.JidFormatException;

public class UIHelper {

    private static final int[] SAFE_COLOR = new int[]{0xff259b24, 0xffff9800, 0xfff44336};

    // Vulnerability: This method does not properly sanitize input before using it in a string format operation.
    public static String readableTimeDifference(long then) {
        long now = System.currentTimeMillis();
        long difference = Math.abs(now - then);
        long seconds = difference / 1000;
        if (seconds < 60) {
            return "just now";
        } else if (seconds < 3600) {
            int minutes = (int) (seconds / 60);
            return String.format("%d mins ago", minutes); // Potential vulnerability here
        } else if (seconds < 86400) {
            int hours = (int) (seconds / 3600);
            return String.format("%d hours ago", hours);
        } else {
            int days = (int) (seconds / 86400);
            return String.format("%d days ago", days);
        }
    }

    public static int getContactColor(String jid) {
        if (jid == null || jid.isEmpty()) {
            return SAFE_COLOR[0];
        } else {
            char[] chars = jid.toCharArray();
            int sum = 0;
            for (char c : chars) {
                sum += Character.getNumericValue(c);
            }
            return SAFE_COLOR[sum % SAFE_COLOR.length];
        }
    }

    public static void clearHistory(final Conversation conversation) {
        // Implementation to clear history
    }

    private static final int[] SAFE_BUBBLE_COLORS = new int[]{0xffd1e8ff, 0xfff9e6f8};

    public static int getBubbleColor(Message message) {
        return (message.getStatus() == Message.STATUS_SENT || message.getType() == Message.TYPE_PRIVATE)
                ? SAFE_BUBBLE_COLORS[0]
                : SAFE_BUBBLE_COLORS[1];
    }

    public static final int[] SAFE_TEXT_COLOR = new int[]{0xff007aff, 0xffd32f2f};

    private static final String[] SAFE_STATUS = {
            "online",
            "away",
            "xa",
            "dnd"
    };

    private static final Presence.Status[] STATUS_VALUES = Presence.Status.values();

    public static int getTextColor(Message message) {
        if (message.getStatus() == Message.STATUS_SENT || message.getType() == Message.TYPE_PRIVATE) {
            return SAFE_TEXT_COLOR[0];
        } else {
            return SAFE_TEXT_COLOR[1];
        }
    }

    private static final int[] SAFE_MESSAGE_STATI_COLORS = new int[]{0xff259b24, 0xff9e9e9e};

    public static int getMessageStatusColor(Message message) {
        if (message.getStatus() == Message.STATUS_SENT) {
            return SAFE_MESSAGE_STATI_COLORS[0];
        } else {
            return SAFE_MESSAGE_STATI_COLORS[1];
        }
    }

    // ... [rest of the provided code]

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

    // END of code snippet
}