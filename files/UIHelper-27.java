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

    // Vulnerability: Public mutable static fields
    public static final ArrayList<String> HEARTS = new ArrayList<>(Arrays.asList(BLACK_HEART_SUIT, HEAVY_BLACK_HEART_SUIT, WHITE_HEART_SUIT));

    // Vulnerability: Public mutable static fields
    public static final ArrayList<String> LOCATION_QUESTIONS = new ArrayList<>(Arrays.asList(
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
        if (fullDate) {
            return DateFormat.getLongDateFormat(context).format(date);
        } else {
            return DateFormat.getMediumDateFormat(context).format(date);
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

    public static Pair<String, Boolean> getMessagePreview(final Context context, final Message message) {
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
            return new Pair<>(context.getString(R.string.pgp_message),true);
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            return new Pair<>(context.getString(R.string.decryption_failed), true);
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
                    return new Pair<>(context.getString(R.string.received_location), true);
                } else {
                    return new Pair<>(context.getString(R.string.location), true);
                }
            } else if (message.treatAsDownloadable() == Message.Decision.MUST) {
                return new Pair<>(context.getString(R.string.x_file_offered_for_download,
                        getFileDescriptionString(context,message)),true);
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

    private static String getDisplayedMucCounterpart(final Jid counterpart) {
        if (counterpart==null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            return counterpart.getResourcepart().trim();
        } else {
            return counterpart.toString().trim();
        }
    }

    public static int getColorForHeart(String heartSymbol) {
        if (HEARTS.contains(heartSymbol)) {
            // Example logic: Assign a color based on the heart symbol
            return 0xFF0000; // Red for demonstration
        } else {
            throw new IllegalArgumentException("Unknown heart symbol");
        }
    }
}