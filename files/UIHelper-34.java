package com.example.conversations; // Hypothetical package name

import android.content.Context;
import androidx.appcompat.widget.PopupMenu;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UIHelper {

    private static final String[] LOCATION_QUESTIONS = {
        "where are you",
        "location",
        "where r u",
        // ... more potential location questions
    };

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
            // Log the exception or handle it as appropriate
            return false;
        }
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

    public static String translateType(Context context, String type) {
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

    public static String getFileDescriptionString(Context context, Message message) {
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

    public static String getMessageDisplayName(Message message) {
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

    public static boolean receivedLocationQuestion(Message message) {
        if (message == null
                || message.getStatus() != Message.STATUS_RECEIVED
                || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody() == null ? null : message.getBody().trim().toLowerCase(Locale.getDefault());
        body = body.replace("?","").replace("¿","");
        for (String question : LOCATION_QUESTIONS) {
            if (question.equals(body)) {
                return true;
            }
        }
        return false;
    }

    public static String getDisplayedMucCounterpart(Jid counterpart) {
        if (counterpart == null) {
            return "";
        } else if (!counterpart.isBareJid()) {
            return counterpart.getResourcepart().trim();
        } else {
            return counterpart.toString().trim();
        }
    }

    // ... other methods and utilities ...
}