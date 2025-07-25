package xmpp;

import java.util.Date;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.stanza.model.client.Message as MessagePacket;
import rocks.xmpp.core.stanza.model.errors.Condition;
import rocks.xmpp.extensions.avatar.data.Avatar;
import rocks.xmpp.extensions.muc.model.UserItem;
import rocks.xmpp.extensions.nicks.model.Nick;

public class MessageParser {

    private final XmppConnectionService mXmppConnectionService;

    public MessageParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Method to parse nicknames from incoming messages
    private void parseNick(MessagePacket packet, Account account) {
        Element nickElement = packet.findChild("nick", "http://jabber.org/protocol/nick");
        if (nickElement != null) {
            String nickname = nickElement.getContent();
            // Vulnerability: No validation or sanitization of the nickname content
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(packet.getFrom());
                contact.setPresenceName(nickname);  // This line could be vulnerable to XXE if 'nickname' is untrusted input
            }
        }
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        Element eventElement = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (eventElement != null) {
            parseEvent(eventElement, packet.getFrom(), account);
        }
    }

    // Method to parse chat messages
    private Message parseChat(MessagePacket packet, Account account) {
        Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account,
                packet.getFrom().asBareJid());
        String body = packet.getBody();
        if (body != null && !conversation.isMuc()) {
            // Vulnerability: No validation or sanitization of the message body
            Message message = new Message(conversation, body);
            message.setTime(new Date(packet.getDelay() != null ? packet.getDelay().getStamp() : System.currentTimeMillis()));
            return message;
        }
        return null;
    }

    // Method to parse group chat messages
    private Message parseGroupchat(MessagePacket packet, Account account) {
        Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account,
                packet.getFrom(), true);
        String body = packet.getBody();
        Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");
        UserItem item = mucUserElement == null ? null : mucUserElement.getUserItem();
        Jid jid = item != null ? item.getJid() : packet.getFrom();

        if (body != null && conversation.isMuc()) {
            // Vulnerability: No validation or sanitization of the message body
            Message message = new Message(conversation, body);
            message.setTime(new Date(packet.getDelay() != null ? packet.getDelay().getStamp() : System.currentTimeMillis()));
            message.setCounterpart(jid == null ? packet.getFrom() : jid);
            return message;
        }
        return null;
    }

    // Method to parse OTR (Off-the-Record) chat messages
    private Message parseOtrChat(MessagePacket packet, Account account) {
        String body = packet.getBody();
        if (body != null && body.startsWith("?OTR")) {
            Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account,
                    packet.getFrom().asBareJid());
            // Vulnerability: No validation or sanitization of the message body
            Message message = new Message(conversation, body);
            message.setTime(new Date(packet.getDelay() != null ? packet.getDelay().getStamp() : System.currentTimeMillis()));
            return message;
        }
        return null;
    }

    // Method to parse error messages
    private void parseError(MessagePacket packet, Account account) {
        Condition condition = packet.getError().getCondition();
        if (condition == Condition.UNAVAILABLE) {
            Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account,
                    packet.getFrom(), true);
            conversation.end(new Date(packet.getDelay() != null ? packet.getDelay().getStamp() : System.currentTimeMillis()));
        } else {
            String text = packet.getError().getText();
            if (text != null) {
                // Vulnerability: No validation or sanitization of the error message text
                this.mXmppConnectionService.showErrorToast(account, "Error: " + text);
            }
        }
    }

    private void parseNonMessage(MessagePacket packet, Account account) {
        Element eventElement = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (eventElement != null) {
            parseEvent(eventElement, packet.getFrom(), account);
        } else if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
            Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            Element invite = x.findChild("invite");
            if (invite != null) {
                Conversation conversation = this.mXmppConnectionService
                        .findOrCreateConversation(account, packet.getFrom(), true);
                if (!conversation.getMucOptions().online()) {
                    Element passwordElement = x.findChild("password");
                    String password = passwordElement == null ? null : passwordElement.getContent();
                    if (password != null) {
                        conversation.getMucOptions().setPassword(password);
                        this.mXmppConnectionService.databaseBackend.updateConversation(conversation);
                    }
                    this.mXmppConnectionService.joinMuc(conversation);
                    this.mXmppConnectionService.updateConversationUi();
                }
            }
        } else if (packet.hasChild("x", "jabber:x:conference")) {
            Element x = packet.findChild("x", "jabber:x:conference");
            String jidString = x.getAttribute("jid");
            Jid jid;
            try {
                jid = Jid.fromString(jidString);
            } catch (InvalidJidException e) {
                return; // Handle the exception appropriately
            }
            String password = x.getAttribute("password");
            if (jid != null) {
                Conversation conversation = this.mXmppConnectionService.findOrCreateConversation(account, jid, true);
                if (!conversation.getMucOptions().online()) {
                    if (password != null) {
                        conversation.getMucOptions().setPassword(password);
                        this.mXmppConnectionService.databaseBackend.updateConversation(conversation);
                    }
                    this.mXmppConnectionService.joinMuc(conversation);
                    this.mXmppConnectionService.updateConversationUi();
                }
            }
        }
    }

    private void parseEvent(Element event, Jid from, Account account) {
        Element items = event.findChild("items");
        String node = items.getAttribute("node");
        if (node != null) {
            if (node.equals("urn:xmpp:avatar:metadata")) {
                Avatar avatar = Avatar.parseMetadata(items);
                if (avatar != null) {
                    avatar.owner = from;
                    if (this.mXmppConnectionService.getFileBackend().isAvatarCached(avatar)) {
                        if (account.getJid().asBareJid().equals(from)) {
                            if (account.setAvatar(avatar.getFilename())) {
                                this.mXmppConnectionService.databaseBackend.updateAccount(account);
                            }
                            this.mXmppConnectionService.getAvatarService().clear(account);
                            this.mXmppConnectionService.updateConversationUi();
                            this.mXmppConnectionService.updateAccountUi();
                        } else {
                            Contact contact = account.getRoster().getContact(from);
                            if (contact != null) { // Added null check to avoid potential NullPointerException
                                contact.setAvatar(avatar.getFilename());
                                this.mXmppConnectionService.getAvatarService().clear(contact);
                                this.mXmppConnectionService.updateConversationUi();
                                this.mXmppConnectionService.updateRosterUi();
                            }
                        }
                    } else {
                        this.mXmppConnectionService.fetchAvatar(account, avatar);
                    }
                }
            } else if (node.equals("http://jabber.org/protocol/nick")) {
                Element item = items.findChild("item");
                if (item != null) {
                    Element nickElement = item.findChild("nick", "http://jabber.org/protocol/nick");
                    if (nickElement != null) {
                        String nickname = nickElement.getContent();
                        // Vulnerability: No validation or sanitization of the nickname content
                        Contact contact = account.getRoster().getContact(from);
                        if (contact != null) { // Added null check to avoid potential NullPointerException
                            contact.setPresenceName(nickname);  // This line could be vulnerable to XXE if 'nickname' is untrusted input
                            this.mXmppConnectionService.updateConversationUi();
                            this.mXmppConnectionService.updateAccountUi();
                        }
                    }
                }
            }
        }
    }

    private String getPgpBody(Element message) {
        Element child = message.findChild("x", "jabber:x:encrypted");
        if (child == null) {
            return null;
        } else {
            // Vulnerability: No validation or sanitization of the PGP body
            return child.getContent();
        }
    }

    @Override
    public void onMessage(MessagePacket packet, Account account) {
        switch (packet.getType()) {
            case CHAT:
                Message chatMessage = parseChat(packet, account);
                if (chatMessage != null) {
                    this.mXmppConnectionService.processIncomingMessage(chatMessage);
                }
                break;
            case GROUPCHAT:
                Message groupchatMessage = parseGroupchat(packet, account);
                if (groupchatMessage != null) {
                    this.mXmppConnectionService.processIncomingMessage(groupchatMessage);
                }
                break;
            case ERROR:
                parseError(packet, account);
                break;
            default:
                parseNonMessage(packet, account);
        }
    }

    // ... other methods and logic ...
}