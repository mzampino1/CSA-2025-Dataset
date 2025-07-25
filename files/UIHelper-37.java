package eu.siacs.conversations.utils;

import android.content.Context;
import java.util.List;
import java.util.Locale;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.AxolotlService;
import eu.siacs.conversations.ui.adapter.ListItem;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.muc.MucOptions;

public class UIHelper {

    private static final int[] SAFE_COLORS = new int[]{
            0xff1abc9c, // TURQUOISE
            0xff2ecc71, // EMERALD
            0xff3498db, // PETER PAN
            0xff9b59b6, // AMETHYST
            0xf1c40f,   // SUN FLOWER
            0xe67e22,   // CARROT
            0xe74c3c,   // ALIZARIN
            0x95a5a6,   // CONCRETE
            0x16a085,   // WET ASPHALT
            0x27ae60,   // NEON CARROT
            0x2980b9,   // BETA
            0x8e44ad,   // WISTERIA
            0xf39c12,   // ORANGE
            0xd35400,   // PUMPKIN
            0xc0392b,   // POMEGRANATE
            0xbdc3c7    // SILVER
    };

    public static int getCorrespondingColor(int account, String name) {
        if (name == null || name.isEmpty()) {
            return SAFE_COLORS[account % SAFE_COLORS.length];
        }
        int sum = 0;
        for (char c : name.toLowerCase().toCharArray()) {
            sum += Character.getNumericValue(c);
        }
        return SAFE_COLORS[(sum + account) % SAFE_COLORS.length];
    }

    public static void colorAccessibleTextView(int color, eu.siacs.conversations.ui.widget.AccessibleTextView textView) {
        int accessibleForegroundColor = ColorUtils.calculateContrastColor(color, 0xFF000000, true);
        textView.setTextColor(accessibleForegroundColor);
    }

    /**
     * Returns a string representation of the time elapsed since a given timestamp.
     *
     * @param context The application context used for fetching resources
     * @param timestamp The timestamp to calculate the elapsed time from
     * @return A human-readable string representing the elapsed time
     */
    public static String readableTimeDifference(Context context, long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = Math.max(0, diff / 1000);
        if (seconds < 60) {
            return context.getString(R.string.just_now);
        } else if (seconds < 3600) {
            int minutes = (int) (seconds / 60);
            return context.getResources().getQuantityString(
                    R.plurals.minutes_ago, minutes, minutes
            );
        } else if (seconds < 86400) {
            int hours = (int) (seconds / 3600);
            return context.getResources().getQuantityString(
                    R.plurals.hours_ago, hours, hours
            );
        } else {
            int days = (int) (seconds / 86400);
            return context.getResources().getQuantityString(
                    R.plurals.days_ago, days, days
            );
        }
    }

    /**
     * Determines if the provided message is a location question.
     *
     * @param message The message to check
     * @return True if the message is a known location question, false otherwise
     */
    public static boolean receivedLocationQuestion(Message message) {
        if (message == null || message.getStatus() != Message.STATUS_RECEIVED || message.getType() != Message.TYPE_TEXT) {
            return false;
        }
        String body = message.getBody() == null ? null : message.getBody().trim().toLowerCase(Locale.getDefault());
        body = body.replace("?", "").replace("¿", "");
        return LOCATION_QUESTIONS.contains(body);
    }

    /**
     * Returns a tag for the presence status to be used in list items.
     *
     * @param context The application context
     * @param status The presence status
     * @return A ListItem.Tag object containing the display name and color for the status
     */
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

    /**
     * Returns a string description of the file type based on MIME type.
     *
     * @param context The application context
     * @param message The message containing the file information
     * @return A human-readable string describing the file type
     */
    public static String getFileDescriptionString(Context context, Message message) {
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

    /**
     * Returns the display name of a user in a MUC conversation.
     *
     * @param user The user whose display name is to be retrieved
     * @return The display name of the user, or null if not available
     */
    public static String getDisplayName(MucOptions.User user) {
        Contact contact = user.getContact();
        if (contact != null) {
            return contact.getDisplayName();
        } else {
            final String name = user.getName();
            if (name != null) {
                return name;
            }
            final Jid realJid = user.getRealJid();
            if (realJid != null) {
                return JidHelper.localPartOrFallback(realJid);
            }
            return null;
        }
    }

    /**
     * Concatenates the names of up to a specified number of users.
     *
     * @param users A list of MUC users
     * @param max The maximum number of user names to concatenate
     * @return A string containing concatenated user names, separated by commas
     */
    public static String concatNames(List<MucOptions.User> users, int max) {
        StringBuilder builder = new StringBuilder();
        final boolean shortNames = users.size() >= 3;
        for (int i = 0; i < Math.min(users.size(), max); ++i) {
            if (builder.length() != 0) {
                builder.append(", ");
            }
            final String name = UIHelper.getDisplayName(users.get(i));
            if (name != null) {
                builder.append(shortNames ? name.split("\\s+")[0] : name);
            }
        }
        return builder.toString();
    }

    /**
     * Concatenates the names of all users in a list.
     *
     * @param users A list of MUC users
     * @return A string containing concatenated user names, separated by commas
     */
    public static String concatNames(List<MucOptions.User> users) {
        return UIHelper.concatNames(users, users.size());
    }

    /**
     * Returns a hint for the message input field based on conversation encryption status.
     *
     * @param context The application context
     * @param conversation The conversation object
     * @return A string hint for the message input field
     */
    public static String getMessageHint(Context context, Conversation conversation) {
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                if (Config.multipleEncryptionChoices()) {
                    return context.getString(R.string.send_as_unencrypted_message);
                } else {
                    return context.getString(R.string.enter_your_message);
                }
            case Message.ENCRYPTION_AXOLOTL:
                return context.getString(R.string.encrypted_message_hint);
            default:
                return context.getString(R.string.enter_your_message);
        }
    }

    /**
     * Returns the display name of a user in a MUC conversation.
     *
     * @param message The message for which to get the display name
     * @return The display name of the sender, or null if not available
     */
    public static String getMessageDisplayName(Message message) {
        Conversation conversation = message.getConversation();
        if (conversation.isPrivate()) {
            Contact contact = message.getContact();
            return contact != null ? contact.getDisplayName() : null;
        } else {
            MucOptions.User user = message.getMucUser();
            return UIHelper.getDisplayName(user);
        }
    }

    /**
     * Returns the resource part of a JID.
     *
     * @param jid The JID from which to extract the resource
     * @return The resource part of the JID, or an empty string if no resource is present
     */
    public static String getResource(Jid jid) {
        return jid.getResourcepart() != null ? jid.getResourcepart().toString() : "";
    }

    /**
     * Returns a displayable name for a conversation.
     *
     * @param context The application context
     * @param conversation The conversation object
     * @return A string containing the display name of the conversation
     */
    public static String getConversationName(Context context, Conversation conversation) {
        if (conversation.isPrivate()) {
            Contact contact = conversation.getContact();
            return contact != null ? contact.getDisplayName() : JidHelper.localPartOrFallback(conversation.getJid().asBareJid());
        } else {
            return conversation.getName() != null ? conversation.getName() : context.getString(R.string.conversation_with, JidHelper.localpart(conversation.getJid()));
        }
    }

    /**
     * Returns the appropriate color for a conversation based on its name.
     *
     * @param account The account number
     * @param conversation The conversation object
     * @return An integer representing the color code
     */
    public static int getConversationColor(int account, Conversation conversation) {
        if (conversation.isPrivate()) {
            Contact contact = conversation.getContact();
            return UIHelper.getCorrespondingColor(account, contact != null ? contact.getDisplayName() : JidHelper.localPartOrFallback(conversation.getJid().asBareJid()));
        } else {
            return UIHelper.getCorrespondingColor(account, conversation.getName());
        }
    }

    /**
     * Determines if a position is the last item in a list.
     *
     * @param position The current position
     * @param count The total number of items in the list
     * @return True if the position is the last item, false otherwise
     */
    public static boolean isLastPosition(int position, int count) {
        return position == (count - 1);
    }

    /**
     * Determines if a position is the first item in a list.
     *
     * @param position The current position
     * @return True if the position is the first item, false otherwise
     */
    public static boolean isFirstPosition(int position) {
        return position == 0;
    }
}