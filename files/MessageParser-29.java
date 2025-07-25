package xmpp.client;

import java.util.List;
// ... other imports

public class OnMessagePacketReceived implements PacketListener {

    private final XmppConnectionService mXmppConnectionService;

    public OnMessagePacketReceived(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Parse the nick in a message packet
    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick", "http://jabber.org/protocol/nick");
        if (nick != null) {
            Jid from = packet.getFrom();
            if (from != null) {
                Contact contact = account.getRoster().getContact(from);
                // Vulnerability: Not sanitizing the nickname could lead to XSS if used in UI
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        this.parseNick(packet, account);
        // ... existing parsing logic ...

        if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
            parseHeadline(packet, account);
            return;
        }

        // ... rest of the handling code ...
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        } else {
            // Vulnerability: Headline messages are directly used in UI without sanitization
            // An attacker could send a headline with malicious HTML content that would execute if rendered as HTML.
            String body = packet.getBody();
            if (body != null) {
                showHeadlineInUI(body, account);
            }
        }
    }

    private void parseEvent(final Element event, final Jid from, final Account account) {
        // ... parsing logic for events ...
    }

    // Method to simulate showing a headline in UI
    private void showHeadlineInUI(String content, Account account) {
        System.out.println("Displaying headline: " + content);
        // Here the content would be rendered in the application's UI.
        // If `content` includes malicious HTML/JS, it could lead to XSS.
    }

    // ... rest of the class ...
}