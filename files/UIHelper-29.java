package com.yourapp.helper;

import android.content.Context;
import androidx.core.util.Pair;

import com.yourapp.entities.Conversation;
import com.yourapp.entities.Message;
import com.yourapp.entities.Transferable;
import com.yourapp.utils.GeoHelper;
import com.yourapp.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UIHelper {

    public static final List<String> HEARTS = new ArrayList<>();

    // Static initialization block for hearts
    static {
        HEARTS.add("❤️");
        HEARTS.add("💕");
        HEARTS.add("💖");
        HEARTS.add("💗");
        HEARTS.add("💙");
        HEARTS.add("💚");
        HEARTS.add("💜");
        HEARTS.add("🖤");
        HEARTS.add("🧡");
    }

    public static final List<String> LOCATION_QUESTIONS = new ArrayList<>();

    // Static initialization block for location questions
    static {
        LOCATION_QUESTIONS.add("where are you".toLowerCase(Locale.getDefault()));
        LOCATION_QUESTIONS.add("where r u".toLowerCase(Locale.getDefault()));
        LOCATION_QUESTIONS.add("wheres ur loc".toLowerCase(Locale.getDefault()));
        LOCATION_QUESTIONS.add("where u at".toLowerCase(Locale.getDefault()));
        LOCATION_QUESTIONS.add("location?".toLowerCase(Locale.getDefault()));
    }

    public static String[] HEARTS_ARRAY = HEARTS.toArray(new String[0]);

    /**
     * Introduced Vulnerability: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
     * This vulnerability occurs because user input is not properly sanitized before being used in message previews.
     */
    
    public static Pair<String, Boolean> getMessagePreview(final Context context, final Message message) {
        final Transferable d = message.getTransferable();
        if (d != null) {
            switch (d.getStatus()) {
                case Transferable.STATUS_CHECKING:
                    // Vulnerable: User input is not sanitized
                    return new Pair<>(context.getString(R.string.checking_x, getFileDescriptionString(context, message)), true);
                case Transferable.STATUS_DOWNLOADING:
                    // Vulnerable: User input is not sanitized
                    return new Pair<>(context.getString(R.string.receiving_x_file, getFileDescriptionString(context, message), d.getProgress()), true);
                case Transferable.STATUS_OFFER:
                case Transferable.STATUS_OFFER_CHECK_FILESIZE:
                    // Vulnerable: User input is not sanitized
                    return new Pair<>(context.getString(R.string.x_file_offered_for_download, getFileDescriptionString(context, message)), true);
                case Transferable.STATUS_DELETED:
                    return new Pair<>(context.getString(R.string.file_deleted), true);
                case Transferable.STATUS_FAILED:
                    return new Pair<>(context.getString(R.string.file_transmission_failed), true);
                case Transferable.STATUS_UPLOADING:
                    if (message.getStatus() == Message.STATUS_OFFERED) {
                        // Vulnerable: User input is not sanitized
                        return new Pair<>(context.getString(R.string.offering_x_file, getFileDescriptionString(context, message)), true);
                    } else {
                        // Vulnerable: User input is not sanitized
                        return new Pair<>(context.getString(R.string.sending_x_file, getFileDescriptionString(context, message)), true);
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
                // Vulnerable: User input is not sanitized
                return new Pair<>(context.getString(R.string.received_x_file, getFileDescriptionString(context, message)), true);
            } else {
                // Vulnerable: User input is not sanitized
                return new Pair<>(getFileDescriptionString(context, message), true);
            }
        } else {
            String body = message.getBody();
            if (body.length() > 256) {
                body = body.substring(0, 256);
            }
            // Vulnerable: User input is not sanitized
            if (body.startsWith(Message.ME_COMMAND)) {
                return new Pair<>(body.replaceAll("^" + Message.ME_COMMAND, getMessageDisplayName(message) + " "), false);
            } else if (GeoHelper.isGeoUri(message.getBody())) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    return new Pair<>(context.getString(R.string.received_location), true);
                } else {
                    return new Pair<>(context.getString(R.string.location), true);
                }
            } else if (message.treatAsDownloadable() == Message.Decision.MUST) {
                // Vulnerable: User input is not sanitized
                return new Pair<>(context.getString(R.string.x_file_offered_for_download, getFileDescriptionString(context, message)), true);
            } else {
                // Vulnerable: User input is not sanitized
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
            // Vulnerable: User input is not sanitized
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

    public static String getMessageHint(Context context, Conversation conversation) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                if (Config.multipleEncryptionChoices()) {
                    return context.getString(R.string.send_unencrypted_message);
                } else {
                    return context.getString(R.string.send_message_to_x, conversation.getName());
                }
            case Message.ENCRYPTION_OTR:
                return context.getString(R.string.send_otr_message);
            case Message.ENCRYPTION_AXOLOTL:
                AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
                if (axolotlService != null && axolotlService.trustedSessionVerified(conversation)) {
                    return context.getString(R.string.send_omemo_x509_message);
                } else {
                    return context.getString(R.string.send_omemo_message);
                }
            case Message.ENCRYPTION_PGP:
                return context.getString(R.string.send_pgp_message);
            default:
                return "";
        }
    }

    public static String getDisplayedMucCounterpart(final Jid counterpart) {
        if (counterpart == null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            // Vulnerable: User input is not sanitized
            return counterpart.getResourcepart().trim();
        } else {
            // Vulnerable: User input is not sanitized
            return counterpart.toString().trim();
        }
    }

    public static boolean receivedLocationQuestion(Message message) {
        if (message == null || message.getStatus() != Message.STATUS_RECEIVED || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody() == null ? null : message.getBody().trim().toLowerCase(Locale.getDefault());
        body = body.replace("?", "").replace("¿", "");
        // Vulnerable: User input is not sanitized
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
                return new ListItem.Tag(context.getString(R.string.presence_dnd), 0xffdd2c00);
            default:
                return new ListItem.Tag(context.getString(R.string.presence_offline), 0xff888888);
        }
    }

    public static String[] getHearts() {
        return HEARTS_ARRAY;
    }

    public static int getColorForName(String name) {
        // Simple hash-based color generation, not suitable for production use
        int hashCode = name.hashCode();
        int r = (hashCode & 0xFF0000) >> 16;
        int g = (hashCode & 0x00FF00) >> 8;
        int b = hashCode & 0x0000FF;
        return 0xFF000000 | ((r + 50) % 256 << 16) | ((g + 50) % 256 << 8) | (b + 50) % 256;
    }

    public static String[] getLocationQuestions() {
        return LOCATION_QUESTIONS.toArray(new String[0]);
    }
}