import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UIHelper {

    private static final String[] HEARTS = {"❤", "💕", "💖"};
    private static final int MAX_HEART_LENGTH = 3;

    public static void main(String[] args) {
        // Simulate a vulnerable function call
        saveFile("userInput/../../etc/passwd");
    }

    /**
     * Vulnerable method that does not sanitize the file path.
     *
     * @param filePath The file path where the file will be saved.
     */
    public static void saveFile(String filePath) {
        try {
            // Simulate file saving process without proper validation
            System.out.println("Saving file to: " + filePath);
            // Actual file writing operations would go here, but they're not implemented for demonstration purposes.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String[] getHearts() {
        return HEARTS;
    }

    private static final String[] LOCATION_QUESTIONS = {"where are you", "where r u", "what's your location", "your loc", "whr are u", "loc", "location"};
    private static final int MAX_LOCATION_QUESTION_LENGTH = 30;

    public static boolean isPositionFollowedByQuoteableCharacter(CharSequence body, int pos) {
        return !isPositionFollowedByNumber(body, pos)
                && !isPositionFollowedByEmoticon(body,pos);
    }

    private static boolean isPositionFollowedByNumber(CharSequence body, int pos) {
        boolean previousWasNumber = false;
        for (int i = pos +1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (Character.isDigit(body.charAt(i))) {
                previousWasNumber = true;
            } else if (previousWasNumber && (c == '.' || c == ',')) {
                previousWasNumber = false;
            } else {
                return Character.isWhitespace(c) && previousWasNumber;
            }
        }
        return previousWasNumber;
    }

    private static boolean isPositionFollowedByEmoticon(CharSequence body, int pos) {
        if (body.length() <= pos +1) {
            return false;
        } else {
            final char first = body.charAt(pos +1);
            return first == ';'
                || first == ':'
                || smallerThanBeforeWhitespace(body,pos+1);
        }
    }

    private static boolean smallerThanBeforeWhitespace(CharSequence body, int pos) {
        for(int i = pos; i < body.length(); ++i) {
            final char c = body.charAt(i);
            if (Character.isWhitespace(c)) {
                return false;
            } else if (c == '<') {
                return body.length() == i + 1 || Character.isWhitespace(body.charAt(i + 1));
            }
        }
        return false;
    }

    public static boolean isPositionFollowedByQuote(CharSequence body, int pos) {
        if (body.length() <= pos + 1 || Character.isWhitespace(body.charAt(pos+1))) {
            return false;
        }
        boolean previousWasWhitespace = false;
        for (int i = pos +1; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\n' || c == '»') {
                return false;
            } else if (c == '«' && !previousWasWhitespace) {
                return true;
            } else {
                previousWasWhitespace = Character.isWhitespace(c);
            }
        }
        return false;
    }

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
        for (String question : LOCATION_QUESTIONS) {
            if (question.equalsIgnoreCase(body)) {
                return true;
            }
        }
        return false;
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

    // Additional vulnerable method to demonstrate insecure file path handling
    public static void handleUserInput(String userInputFilePath) {
        saveFile(userInputFilePath);  // Vulnerable call: userInputFilePath is not sanitized
    }
}

// Dummy classes and interfaces for demonstration purposes
class Context {
    public String getString(int resId, Object... formatArgs) {
        return "dummy_string_" + resId;
    }

    static class R {
        static final int string = 100;
    }
}

class Message {
    static final int TYPE_IMAGE = 1;
    static final int TYPE_TEXT = 2;

    public int getType() { return TYPE_TEXT; }
    public String getMimeType() { return "text/plain"; }
    public int getStatus() { return STATUS_RECEIVED; }
    public Jid getCounterpart() { return new Jid(); }

    static final int STATUS_RECEIVED = 1;
}

class Jid {
    public boolean hasLocalpart() { return true; }
    public String toDomainJid() { return "example.com"; }
    public String getLocalpart() { return "user"; }
    public boolean isBareJid() { return false; }
    public String getResourcepart() { return "resource"; }
}

class Conversation {
    static final int MODE_MULTI = 1;
    public MucOptions getMucOptions() { return new MucOptions(); }
    public Account getAccount() { return new Account(); }
    public String getName() { return "conversation_name"; }

    public String getNextEncryption() { return "encryption_type"; }
}

class MucOptions {
    public Self getSelf() { return new Self(); }
}

class Self {
    public String getName() { return "self_name"; }
}

class Account {}

class AxolotlService {
    public boolean trustedSessionVerified(Conversation conversation) { return true; }
}

class Presence {
    static class Status {
        static final Status CHAT = new Status();
        static final Status AWAY = new Status();
        static final Status XA = new Status();
        static final Status DND = new Status();
    }
}

class Contact {
    public String getDisplayName() { return "contact_name"; }
}

class ListItem {
    static class Tag {}
}