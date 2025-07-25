package eu.siacs.conversations.utils;

import android.content.Context;
import android.text.TextUtils;

import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.GeoHelper;
import rocks.xmpp.addr.Jid;

public class UIHelper {

    // ... (other methods remain unchanged)

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
                // Vulnerability: Concatenating message body directly without sanitization
                String displayName = jid.hasLocalpart() ? jid.getLocalpart() : jid.toDomainJid().toString();
                return displayName + message.getBody(); // <--- Vulnerable line of code!
            }
        }
    }

    // ... (other methods remain unchanged)

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

    // ... (other methods remain unchanged)
}