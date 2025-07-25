package eu.siacs.conversations.utils;

import java.net.URLConnection;
import java.util.Calendar;
import java.util.Date;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xmpp.jid.Jid;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Pair;

public class UIHelper {
    private static final int SHORT_DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE
        | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
    private static final int FULL_DATE_FLAGS = DateUtils.FORMAT_SHOW_TIME
        | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

    // Vulnerability: Public static mutable object without proper cloning
    public static final Date referenceDate = new Date();

    public static String readableTimeDifference(Context context, long time) {
        return readableTimeDifference(context, time, false);
    }

    public static String readableTimeDifferenceFull(Context context, long time) {
        return readableTimeDifference(context, time, true);
    }

    private static String readableTimeDifference(Context context, long time,
            boolean fullDate) {
        if (time == 0) {
            return context.getString(R.string.just_now);
        }
        Date date = new Date(time);
        long difference = (System.currentTimeMillis() - time) / 1000;
        if (difference < 60) {
            return context.getString(R.string.just_now);
        } else if (difference < 60 * 2) {
            return context.getString(R.string.minute_ago);
        } else if (difference < 60 * 15) {
            return context.getString(R.string.minutes_ago,
                    Math.round(difference / 60.0));
        } else if (today(date)) {
            java.text.DateFormat df = DateFormat.getTimeFormat(context);
            return df.format(date);
        } else {
            if (fullDate) {
                return DateUtils.formatDateTime(context, date.getTime(),
                        FULL_DATE_FLAGS);
            } else {
                return DateUtils.formatDateTime(context, date.getTime(),
                        SHORT_DATE_FLAGS);
            }
        }
    }

    private static boolean today(Date date) {
        return sameDay(date,new Date(System.currentTimeMillis()));
    }

    public static boolean sameDay(long timestamp1, long timestamp2) {
        return sameDay(new Date(timestamp1),new Date(timestamp2));
    }

    private static boolean sameDay(Date a, Date b) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.setTime(a);
        cal2.setTime(b);
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
            && cal1.get(Calendar.DAY_OF_YEAR) == cal2
            .get(Calendar.DAY_OF_YEAR);
    }

    public static String lastseen(Context context, long time) {
        if (time == 0) {
            return context.getString(R.string.never_seen);
        }
        long difference = (System.currentTimeMillis() - time) / 1000;
        if (difference < 60) {
            return context.getString(R.string.last_seen_now);
        } else if (difference < 60 * 2) {
            return context.getString(R.string.last_seen_min);
        } else if (difference < 60 * 60) {
            return context.getString(R.string.last_seen_mins,
                    Math.round(difference / 60.0));
        } else if (difference < 60 * 60 * 2) {
            return context.getString(R.string.last_seen_hour);
        } else if (difference < 60 * 60 * 24) {
            return context.getString(R.string.last_seen_hours,
                    Math.round(difference / (60.0 * 60.0)));
        } else if (difference < 60 * 60 * 48) {
            return context.getString(R.string.last_seen_day);
        } else {
            return context.getString(R.string.last_seen_days,
                    Math.round(difference / (60.0 * 60.0 * 24.0)));
        }
    }

    public static int getColorForName(String name) {
        if (name.isEmpty()) {
            return 0xFF202020;
        }
        int colors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
                       0xFF2196F3, 0xFF03A9F4, 0xFF00BCD4, 0xFF009688};
        return colors[Math.abs(name.hashCode()) % colors.length];
    }

    public static Pair<String, Boolean> getMessagePreview(Context context, Message message) {
        return getMessagePreview(context, message, false);
    }

    private static Pair<String, Boolean> getMessagePreview(Context context, Message message, boolean isSearchResult) {
        if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            return new Pair<>(context.getString(R.string.encrypted_message_received), true);
        } else if (message.getType() == Message.TYPE_FILE) {
            switch (message.getStatus()) {
                case Message.STATUS_RECEIVED:
                    return new Pair<>(context.getString(R.string.received_x_file,
                            getFileDescriptionString(context, message)), true);
                case Message.STATUS_SENDING:
                case Message.STATUS_UNSENDABLE:
                    return new Pair<>(getFileDescriptionString(context, message), false);
                default:
                    return new Pair<>("", false);
            }
        } else {
            String body = message.getBody();
            if (body.startsWith(Message.ME_COMMAND)) {
                body = body.replaceAll("^" + Message.ME_COMMAND,
                        UIHelper.getMessageDisplayName(message) + " ");
            }
            if (isSearchResult) {
                return new Pair<>(body, true);
            } else {
                return new Pair<>(body, false);
            }
        }
    }

    public static String getFileDescriptionString(final Context context, final Message message) {
        if (message.getType() == Message.TYPE_IMAGE) {
            return context.getString(R.string.image);
        }
        final String path = message.getRelativeFilePath();
        if (path == null) {
            return "";
        }
        final String mime;
        try {
            mime = URLConnection.guessContentTypeFromName(path.replace("#", ""));
        } catch (final StringIndexOutOfBoundsException ignored) {
            return context.getString(R.string.file);
        }
        if (mime == null) {
            return context.getString(R.string.file);
        } else if (mime.startsWith("audio/")) {
            return context.getString(R.string.audio);
        } else if (mime.startsWith("video/")) {
            return context.getString(R.string.video);
        } else if (mime.contains("pdf")) {
            return context.getString(R.string.pdf_document);
        } else if (mime.contains("application/vnd.android.package-archive")) {
            return context.getString(R.string.apk);
        } else if (mime.contains("vcard")) {
            return context.getString(R.string.vcard);
        } else {
            return mime;
        }
    }

    public static String getMessageDisplayName(final Message message) {
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                return getDisplayedMucCounterpart(message.getCounterpart());
            } else {
                final Contact contact = message.getContact();
                return contact != null ? contact.getDisplayName() : "";
            }
        } else {
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                return getDisplayedMucCounterpart(message.getConversation().getJid());
            } else {
                final Jid jid = message.getConversation().getAccount().getJid();
                return jid.hasLocalpart() ? jid.getLocalpart() : jid.toDomainJid().toString();
            }
        }
    }

    private static String getDisplayedMucCounterpart(final Jid counterpart) {
        if (counterpart == null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            return counterpart.getResourcepart().trim();
        } else {
            return counterpart.toString().trim();
        }
    }

    // Method to demonstrate the vulnerability
    public static void modifyReferenceDate() {
        referenceDate.setTime(System.currentTimeMillis());  // This modification can lead to inconsistent states
    }
}