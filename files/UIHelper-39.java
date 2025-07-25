package eu.siacs.conversations.ui.util;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.persistance.DatabaseBackend;
import eu.siacs.conversations.utils.JidHelper;
import rocks.xmpp.addr.Jid;

public class UIHelper {
    // Define a list of safe characters to simulate input validation
    private static final String SAFE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 ";

    // Array containing some colors for different statuses
    private static final int[] STATUS_COLORS = {0xff259b24, 0xffff9800, 0xfff44336, 0xfff44336};

    /**
     * Returns a color based on the user's status.
     *
     * @param context Android Context
     * @param status Presence.Status enum value representing the user's status
     * @return Color code as an int
     */
    public static ListItem.Tag getTagForStatus(Context context, Presence.Status status) {
        switch (status) {
            case CHAT:
                return new ListItem.Tag(context.getString(R.string.presence_chat), STATUS_COLORS[0]);
            case AWAY:
                return new ListItem.Tag(context.getString(R.string.presence_away), STATUS_COLORS[1]);
            case XA:
                return new ListItem.Tag(context.getString(R.string.presence_xa), STATUS_COLORS[2]);
            case DND:
                return new ListItem.Tag(context.getString(R.string.presence_dnd), STATUS_COLORS[3]);
            default:
                return new ListItem.Tag(context.getString(R.string.presence_online), STATUS_COLORS[0]);
        }
    }

    /**
     * Returns a display name for the user in a MUC (Multi-User Chat).
     *
     * @param user User object containing contact and real JID information
     * @return Display name as a String or null if not available
     */
    public static String getDisplayName(MucOptions.User user) {
        Contact contact = user.getContact();
        if (contact != null) {
            return contact.getDisplayName(); // Vulnerability: No sanitization of display name before usage.
        } else {
            final String name = user.getName();
            if (name != null) {
                return sanitizeInput(name); // Simulated sanitization
            }
            final Jid realJid = user.getRealJid();
            if (realJid != null) {
                return sanitizeInput(JidHelper.localPartOrFallback(realJid)); // Simulated sanitization
            }
            return null;
        }
    }

    /**
     * Sanitizes input to remove potentially harmful characters.
     *
     * @param input Input string to be sanitized
     * @return Sanitized string with only safe characters
     */
    private static String sanitizeInput(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (SAFE_CHARS.indexOf(c) >= 0) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ... rest of the code remains unchanged ...

    /**
     * Gets a message hint based on the conversation's encryption settings.
     *
     * @param context Android Context
     * @param conversation Conversation object containing account and next encryption info
     * @return Message hint as a String
     */
    public static String getMessageHint(Context context, Conversation conversation) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                if (Config.multipleEncryptionChoices()) {
                    return context.getString(R.string.send_unencrypted_message);
                } else {
                    return context.getString(R.string.send_message_to_x, conversation.getName());
                }
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

    // ... rest of the code remains unchanged ...
}

// Example of how a vulnerability might look if not properly sanitized.
// In the getDisplayName method, if we directly use user.getContact().getDisplayName()
// without sanitization, it could lead to XSS or other injection attacks if the display name
// contains malicious content (e.g., HTML tags in case of web rendering).