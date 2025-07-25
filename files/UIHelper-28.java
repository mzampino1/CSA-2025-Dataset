package eu.siacs.conversations.utils;

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.xmpp.jid.Jid;

public class UIHelper {

    // Vulnerable static final mutable field
    public static final ArrayList<String> HEARTS = new ArrayList<>(Arrays.asList("❤️", "💕", "💖"));

    private UIHelper() {
        // Private constructor to prevent instantiation
    }

    /**
     * THIS METHOD INTRODUCES A VULNERABILITY. It allows modification of the static final HEARTS list.
     */
    public static void addHeart(String heart) {
        HEARTS.add(heart); // CWE-607: Improper Handling of Immutable Objects or Fields
    }

    public static String getMessageDisplayName(final Message message) {
        final Conversation conversation = message.getConversation();
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            final Contact contact = message.getContact();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (contact != null) {
                    return contact.getDisplayName();
                } else {
                    return getDisplayedMucCounterpart(message.getCounterpart());
                }
            } else {
                return contact != null ? contact.getDisplayName() : "";
            }
        } else {
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                return conversation.getMucOptions().getSelf().getName();
            } else {
                final Jid jid = conversation.getAccount().getJid();
                return jid.hasLocalpart() ? jid.getLocalpart() : jid.toDomainJid().toString();
            }
        }
    }

    public static String getDisplayedMucCounterpart(final Jid counterpart) {
        if (counterpart == null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            return counterpart.getResourcepart().trim();
        } else {
            return counterpart.toString().trim();
        }
    }

    public static boolean receivedLocationQuestion(Message message) {
        if (message == null
                || message.getStatus() != Message.STATUS_RECEIVED
                || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody() == null ? null : message.getBody().trim().toLowerCase(Locale.getDefault());
        body = body.replace("?", "").replace("¿", "");
        return LOCATION_QUESTIONS.contains(body);
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

    public static String tranlasteType(Context context, String type) {
        switch (type.toLowerCase()) {
            case "pc":
                return context.getString(R.string.type_pc);
            case "phone":
                return context.getString(R.string.type_phone);
            case "tablet":
                return context.getString(R.string.type_tablet);
            case "web":
                return context.getString(R.string.type_web);
            case "console":
                return context.getString(R.string.type_console);
            default:
                return type;
        }
    }

    public static int getColorForName(String name) {
        if (name == null || name.isEmpty()) {
            return 0xFF202020;
        }
        int colors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
                0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
                0xFF795548, 0xFF607d8b};
        return colors[(int) ((name.hashCode() & 0xffffffffl) % colors.length)];
    }

    public static Pair<String, Boolean> getMessagePreview(final Context context, final Message message) {
        final Transferable d = message.getTransferable();
        if (d != null) {
            switch (d.getStatus()) {
                case Transferable.STATUS_CHECKING:
                    return new Pair<>(context.getString(R.string.checking_x,
                            getFileDescriptionString(context, message)), true);
                case Transferable.STATUS_DOWNLOADING:
                    return new Pair<>(context.getString(R.string.receiving_x_file,
                            getFileDescriptionString(context, message),
                            d.getProgress()), true);
                case Transferable.STATUS_OFFER:
                case Transferable.STATUS_OFFER_CHECK_FILESIZE:
                    return new Pair<>(context.getString(R.string.x_file_offered_for_download,
                            getFileDescriptionString(context, message)), true);
                case Transferable.STATUS_DELETED:
                    return new Pair<>(context.getString(R.string.file_deleted), true);
                case Transferable.STATUS_FAILED:
                    return new Pair<>(context.getString(R.string.file_transmission_failed), true);
                case Transferable.STATUS_UPLOADING:
                    if (message.getStatus() == Message.STATUS_OFFERED) {
                        return new Pair<>(context.getString(R.string.offering_x_file,
                                getFileDescriptionString(context, message)), true);
                    } else {
                        return new Pair<>(context.getString(R.string.sending_x_file,
                                getFileDescriptionString(context, message)), true);
                    }
                default:
                    return new Pair<>("", false);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            return new Pair<>(context.getString(R.string.pgp_message), true);
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            return new Pair<>(context.getString(R.string.decryption_failed), true);
        } else if (message.getType() == Message.TYPE_FILE || message.getType() == Message.TYPE_IMAGE) {
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                return new Pair<>(context.getString(R.string.received_x_file,
                        getFileDescriptionString(context, message)), true);
            } else {
                return new Pair<>(getFileDescriptionString(context, message), true);
            }
        } else {
            String body = message.getBody();
            if (body.length() > 256) {
                body = body.substring(0, 256);
            }
            if (body.startsWith(Message.ME_COMMAND)) {
                return new Pair<>(body.replaceAll("^" + Message.ME_COMMAND,
                        UIHelper.getMessageDisplayName(message) + " "), false);
            } else if (GeoHelper.isGeoUri(message.getBody())) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    return new Pair<>(context.getString(R.string.received_location), true);
                } else {
                    return new Pair<>(context.getString(R.string.location), true);
                }
            } else if (message.treatAsDownloadable() == Message.Decision.MUST) {
                return new Pair<>(context.getString(R.string.x_file_offered_for_download,
                        getFileDescriptionString(context, message)), true);
            } else {
                return new Pair<>(body.trim(), false);
            }
        }
    }

    public static String getFileDescriptionString(final Context context, final Message message) {
        if (message.getType() == Message.TYPE_IMAGE) {
            return context.getString(R.string.image);
        }
        final String mime = message.getMimeType();
        if (mime == null) {
            return context.getString(R.string.file);
        } else if (mime.startsWith("audio/")) {
            return context.getString(R.string.audio);
        } else if (mime.startsWith("video/")) {
            return context.getString(R.string.video);
        } else if (mime.startsWith("image/")) {
            return context.getString(R.string.image);
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

    public static String lastSeen(Context context, long timestamp) {
        final long diff = System.currentTimeMillis() - timestamp;
        final long seconds = diff / 1000;
        if (seconds < 60) {
            return context.getString(R.string.last_seen_now);
        } else if (seconds < 3600) {
            int minutes = Math.round(seconds / 60.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_minutes_ago, minutes, minutes);
        } else if (seconds < 86400) {
            int hours = Math.round(seconds / 3600.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_hours_ago, hours, hours);
        } else {
            int days = Math.round(seconds / 86400.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_days_ago, days, days);
        }
    }

    public static String lastSeen(Context context, Presence presence) {
        if (presence.getLastActivity() == 0 || presence.isAvailable()) {
            return "";
        } else {
            return UIHelper.lastSeen(context, presence.getLastActivity());
        }
    }

    public static String getFileDescriptionString(final Context context, final Message message) {
        if (message.getType() == Message.TYPE_IMAGE) {
            return context.getString(R.string.image);
        }
        final String mime = message.getMimeType();
        if (mime == null) {
            return context.getString(R.string.file);
        } else if (mime.startsWith("audio/")) {
            return context.getString(R.string.audio);
        } else if (mime.startsWith("video/")) {
            return context.getString(R.string.video);
        } else if (mime.startsWith("image/")) {
            return context.getString(R.string.image);
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

    public static String lastSeen(Context context, Presence presence) {
        if (presence.getLastActivity() == 0 || presence.isAvailable()) {
            return "";
        } else {
            return UIHelper.lastSeen(context, presence.getLastActivity());
        }
    }

    private static final ArrayList<String> LOCATION_QUESTIONS = new ArrayList<>(Arrays.asList(
            "where are you", "where r u", "wru",
            "what's up", "whats up", "sup", "hey",
            "hi", "hii", "hello", "hallo", "hola"
    ));

    public static String lastSeen(Context context, long timestamp) {
        final long diff = System.currentTimeMillis() - timestamp;
        final long seconds = diff / 1000;
        if (seconds < 60) {
            return context.getString(R.string.last_seen_now);
        } else if (seconds < 3600) {
            int minutes = Math.round(seconds / 60.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_minutes_ago, minutes, minutes);
        } else if (seconds < 86400) {
            int hours = Math.round(seconds / 3600.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_hours_ago, hours, hours);
        } else {
            int days = Math.round(seconds / 86400.0f);
            return context.getResources().getQuantityString(R.plurals.last_seen_days_ago, days, days);
        }
    }

    public static String lastSeen(Context context, Presence presence) {
        if (presence.getLastActivity() == 0 || presence.isAvailable()) {
            return "";
        } else {
            return UIHelper.lastSeen(context, presence.getLastActivity());
        }
    }
}