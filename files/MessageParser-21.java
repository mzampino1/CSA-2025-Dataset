import java.util.logging.Logger;

public class MessageParser {

    private static final Logger LOGGER = Logger.getLogger(MessageParser.class.getName());

    // Method to parse headline messages
    public void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    // Method to parse events in headline messages
    private void parseEvent(Element event, String from, Account account) {
        Element items = event.findChild("items");
        String node = items.getAttribute("node");
        
        if (node != null) {
            if (node.equals("urn:xmpp:avatar:metadata")) {
                Avatar avatar = Avatar.parseMetadata(items);
                if (avatar != null) {
                    avatar.owner = from;
                    // Check if the avatar is cached
                    if (mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                        if (account.getJid().equals(from)) {
                            if (account.setAvatar(avatar.getFilename())) {
                                mXmppConnectionService.databaseBackend.updateAccount(account);
                            }
                            // Clear and update UI for the account
                            mXmppConnectionService.getAvatarService().clear(account);
                            mXmppConnectionService.updateConversationUi();
                            mXmppConnectionService.updateAccountUi();
                        } else {
                            Contact contact = account.getRoster().getContact(from);
                            contact.setAvatar(avatar.getFilename());
                            // Clear and update UI for the contact
                            mXmppConnectionService.getAvatarService().clear(contact);
                            mXmppConnectionService.updateConversationUi();
                            mXmppConnectionService.updateRosterUi();
                        }
                    } else {
                        // Fetch avatar if not cached
                        mXmppConnectionService.fetchAvatar(account, avatar);
                    }
                }
            } else if (node.equals("http://jabber.org/protocol/nick")) {
                Element item = items.findChild("item");
                if (item != null) {
                    Element nick = item.findChild("nick",
                            "http://jabber.org/protocol/nick");
                    if (nick != null) {
                        // Vulnerability: This code does not sanitize the nickname input
                        if (from != null) {
                            Contact contact = account.getRoster().getContact(from);
                            // Set presence name without validation or sanitization, potentially leading to injection attacks
                            contact.setPresenceName(nick.getContent());
                        }
                    }
                }
            }
        }
    }

    // ... rest of the code ...

    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        // ... rest of the method ...
        
        this.parseNick(packet, account);

        // ... rest of the method ...
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                // Vulnerability: This code does not sanitize the nickname input
                // Set presence name without validation or sanitization, potentially leading to injection attacks
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    // ... rest of the class ...
}