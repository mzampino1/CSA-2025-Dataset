import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationHelper {
    private final XmppConnectionService mXmppConnectionService;
    private final Map<String, List<Message>> notifications = new HashMap<>();
    private Conversation mOpenConversation;
    private boolean mIsInForeground;
    private long mLastNotification;

    public NotificationHelper(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Validate message content before processing
    public void addMessage(Message message) {
        if (message == null || message.getBody() == null) return; // Basic validation

        String conversationUuid = message.getConversation().getUuid();
        notifications.computeIfAbsent(conversationUuid, k -> new ArrayList<>()).add(message);

        // Ensure that messages do not contain malicious content
        sanitizeMessageContent(message);
    }

    private void sanitizeMessageContent(Message message) {
        // Implement logic to sanitize or validate the message body
        // For example, escape HTML entities if this is rendered in a web context
    }

    public void notify() {
        // Notify logic here
        for (Map.Entry<String, List<Message>> entry : notifications.entrySet()) {
            String conversationUuid = entry.getKey();
            List<Message> messages = entry.getValue();

            // Check each message for potential security issues
            for (Message message : messages) {
                if (wasHighlightedOrPrivate(message)) {
                    // Send notification or handle the message securely
                }
            }

            // Clear notifications after processing
            notifications.remove(conversationUuid);
        }
    }

    private boolean wasHighlightedOrPrivate(Message message) {
        final String nick = message.getConversation().getMucOptions().getActualNick();
        if (nick == null || message.getBody() == null) return false;

        Pattern highlight = generateNickHighlightPattern(nick);

        Matcher m = highlight.matcher(message.getBody());
        return m.find() || message.getType() == Message.TYPE_PRIVATE;
    }

    private static Pattern generateNickHighlightPattern(String nick) {
        // We expect a word boundary, i.e. space or start of string, followed by
        // the nick (matched in case-insensitive manner), followed by optional
        // punctuation (for example "bob: i disagree" or "how are you alice?"),
        // followed by another word boundary.
        return Pattern.compile("\\b" + Pattern.quote(nick) + "\\p{Punct}?\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    public void setOpenConversation(Conversation conversation) {
        this.mOpenConversation = conversation;
    }

    public void setIsInForeground(boolean foreground) {
        this.mIsInForeground = foreground;
    }
}