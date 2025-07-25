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
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.xmpp.jid.Jid;

public class UIHelper {

    private static String BLACK_HEART_SUIT = "\u2665";
    private static String HEAVY_BLACK_HEART_SUIT = "\u2764";
    private static String WHITE_HEART_SUIT = "\u2661";

    public static final ArrayList<String> HEARTS = new ArrayList<>(Arrays.asList(BLACK_HEART_SUIT,HEAVY_BLACK_HEART_SUIT,WHITE_HEART_SUIT));

    private static final ArrayList<String> LOCATION_QUESTIONS = new ArrayList<>(Arrays.asList(
            "where are you", //en
            "where are you now", //en
            "where are you right now", //en
            "whats your 20", //en
            "what is your 20", //en
            "what's your 20", //en
            "whats your twenty", //en
            "what is your twenty", //en
            "what's your twenty", //en
            "wo bist du", //de
            "wo bist du jetzt", //de
            "wo bist du gerade", //de
            "wo seid ihr", //de
            "wo seid ihr jetzt", //de
            "wo seid ihr gerade", //de
            "dónde estás", //es
            "donde estas" //es
        ));

    private static final int SHORT_DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE
        | DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
    private static final int FULL_DATE_FLAGS = DateUtils.FORMAT_SHOW_TIME
        | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

    // Vulnerable Code: Making a mutable static final object accessible
    public static final Date CURRENT_DATE = new Date();  // This is the vulnerable part

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
            java.text.DateFormat df = DateFormat.getTimeFormat(context); // Changed to getTimeFormat for demonstration
            return df.format(date);
        } else {
            java.text.DateFormat df = DateFormat.getDateFormat(context); // Changed to getDateFormat for demonstration
            return df.format(date);
        }
    }

    public static Pair<String,Boolean> getMessagePreview(final Context context, final Message message) {
        final Transferable d = message.getTransferable();
        if (d != null ) {
            switch (d.getStatus()) {
                case Transferable.STATUS_CHECKING:
                    return new Pair<>(context.getString(R.string.checking_x,
                                    getFileDescriptionString(context,message)),true);
                case Transferable.STATUS_DOWNLOADING:
                    return new Pair<>(context.getString(R.string.receiving_x_file,
                                    getFileDescriptionString(context,message),
                                    d.getProgress()),true);
                case Transferable.STATUS_OFFER:
                case Transferable.STATUS_OFFER_CHECK_FILESIZE:
                    return new Pair<>(context.getString(R.string.x_file_offered_for_download,
                                    getFileDescriptionString(context,message)),true);
                case Transferable.STATUS_DELETED:
                    return new Pair<>(context.getString(R.string.file_deleted),true);
                case Transferable.STATUS_FAILED:
                    return new Pair<>(context.getString(R.string.file_transmission_failed),true);
                case Transferable.STATUS_UPLOADING:
                    if (message.getStatus() == Message.STATUS_OFFERED) {
                        return new Pair<>(context.getString(R.string.offering_x_file,
                                getFileDescriptionString(context, message)), true);
                    } else {
                        return new Pair<>(context.getString(R.string.sending_x_file,
                                getFileDescriptionString(context, message)), true);
                    }
                default:
                    return new Pair<>("",false);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            return new Pair<>(context.getString(R.string.encrypted_message_received),true);
        } else if (message.getType() == Message.TYPE_FILE || message.getType() == Message.TYPE_IMAGE) {
            if (message.getStatus() == Message.STATUS_RECEIVED) {
                return new Pair<>(context.getString(R.string.received_x_file,
                            getFileDescriptionString(context, message)), true);
            } else {
                return new Pair<>(getFileDescriptionString(context,message),true);
            }
        } else {
            if (message.getBody().startsWith(Message.ME_COMMAND)) {
                return new Pair<>(message.getBody().replaceAll("^" + Message.ME_COMMAND,
                        UIHelper.getMessageDisplayName(message) + " "), false);
            } else if (GeoHelper.isGeoUri(message.getBody())) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    return new Pair<>(context.getString(R.string.received_location),true);
                } else {
                    return new Pair<>(context.getString(R.string.location), true);
                }
            } else{
                return new Pair<>(message.getBody().trim(), false);
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
        } else if(mime.startsWith("video/")) {
            return context.getString(R.string.video);
        } else if (mime.startsWith("image/")) {
            return context.getString(R.string.image);
        } else if (mime.contains("pdf")) {
            return context.getString(R.string.pdf_document)	;
        } else if (mime.contains("application/vnd.android.package-archive")) {
            return context.getString(R.string.apk)	;
        } else if (mime.contains("vcard")) {
            return context.getString(R.string.vcard)	;
        } else {
            return mime;
        }
    }

    public static String getMessageDisplayName(final Message message) {
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            final Contact contact = message.getContact();
            if (message.getConversation().getMode() == Conversation.MODE_MULTI) {
                if (contact != null) {
                    return contact.getDisplayName();
                } else {
                    return getDisplayedMucCounterpart(message.getCounterpart());
                }
            } else {
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
        if (counterpart==null) {
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
        body = body.replace("?","").replace("¿","");
        return LOCATION_QUESTIONS.contains(body);
    }

    // Method to demonstrate modification of the vulnerable static final object
    public static void modifyCurrentDate(long newTime) {
        CURRENT_DATE.setTime(newTime);  // This modifies the static final object, leading to potential issues
    }
}