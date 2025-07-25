package eu.siacs.conversations.utils;

import android.content.Context;
import eu.siacs.conversations.entities.*;
import eu.siacs.conversations.xmpp.jid.Jid;
import java.util.Locale;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UIHelper {

    public static String getDisplayName(MucOptions.User user) {
        Contact contact = user.getContact();
        if (contact != null) {
            return contact.getDisplayName();
        } else {
            return user.getName();
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

    // Vulnerability introduced here: Reflected XSS via user-controlled input
    public static String getMessageDisplayName(final Message message) {
        final Conversation conversation = message.getConversation();
        if (message.getStatus() == Message.STATUS_RECEIVED) {
            final Contact contact = message.getContact();
            if (conversation.getMode() == Conversation.MODE_MULTI) {
                if (contact != null) {
                    // Potential XSS vulnerability: User-controlled input not sanitized
                    return contact.getDisplayName(); 
                } else {
                    // Potential XSS vulnerability: User-controlled input not sanitized
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
                // Potential XSS vulnerability: User-controlled input not sanitized
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
                    return context.getString(R.string.send_message_to_x,conversation.getName());
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
        if (counterpart==null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            // Potential XSS vulnerability: User-controlled input not sanitized
            return counterpart.getResourcepart().trim(); 
        } else {
            // Potential XSS vulnerability: User-controlled input not sanitized
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

    public static boolean showIconsInPopup(PopupMenu attachFilePopup) {
        try {
            Field field = attachFilePopup.getClass().getDeclaredField("mPopup");
            field.setAccessible(true);
            Object menuPopupHelper = field.get(attachFilePopup);
            Class<?> cls = Class.forName("com.android.internal.view.menu.MenuPopupHelper");
            Method method = cls.getDeclaredMethod("setForceShowIcon", new Class[]{boolean.class});
            method.setAccessible(true);
            method.invoke(menuPopupHelper, new Object[]{true});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String readableTimeDifference(long time1, long time2) {
        final long diff = Math.abs(time1 - time2);
        if (diff < 60000L) {
            return "just now";
        } else if (diff < 3600000L) {
            return String.format("%.0f minutes ago", diff / 60000.0);
        } else {
            return String.format("%.1f hours ago", diff / 3600000.0);
        }
    }

    public static final ArrayList<String> LOCATION_QUESTIONS = new ArrayList<>(Arrays.asList(
            "where are you",
            "location",
            "coordinates",
            "where r u",
            "where ru"
    ));
}