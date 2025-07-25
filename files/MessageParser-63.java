// MessageGenerator.java
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.core.stanza.Message;

public class MessageGenerator {
    // ... other methods ...

    /**
     * Generates a simple text message for sending.
     *
     * @param account   the XMPP account to send from
     * @param recipient the recipient of the message
     * @param body      the body of the message (user-provided)
     * @return a Message object ready to be sent
     */
    public Message generateTextMessage(Account account, Jid recipient, String body) {
        // Vulnerability: Improper input validation can lead to injection attacks.
        // If 'body' is not properly sanitized and it contains malicious data (e.g., XML tags),
        // it could be used to inject malicious content into the message.

        Message message = new Message(recipient);
        message.setType(Message.Type.CHAT);
        message.setFrom(account.getJid());
        message.addExtension(new Message.Body(body)); // Potential injection point
        return message;
    }

    // ... other methods ...
}

// Message.java (simplified for demonstration)
class Message {
    enum Type { CHAT }
    private Jid from;
    private Jid to;
    private String type;
    private List<Body> extensions;

    public void setFrom(Jid jid) {
        this.from = jid;
    }

    public void setType(Type type) {
        this.type = type.name().toLowerCase();
    }

    public void addExtension(Body body) {
        if (extensions == null) extensions = new ArrayList<>();
        extensions.add(body);
    }

    // ... other methods ...
}

// Body.java (simplified for demonstration)
class Body {
    private String text;

    public Body(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}