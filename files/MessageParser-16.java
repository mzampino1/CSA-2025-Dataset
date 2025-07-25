package eu.siacs.conversations.services;

import android.os.SystemClock;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.Avatar;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;

// This class is responsible for handling incoming message packets and processing them accordingly.
public class MessageProcessor implements PacketReceived {

    private final XmppConnectionService mXmppConnectionService;
    private long lastCarbonMessageReceived = 0L;

    public MessageProcessor(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Vulnerability: This method is not properly validating or sanitizing the input from the message packet.
    // A malicious actor could potentially inject harmful content into the message body, which would then be processed and displayed.
    @Override
    public void onMessagePacketReceived(Account account, MessagePacket packet) {
        Message message = null;
        boolean notify = true;
        if (mXmppConnectionService.getPreferences().getBoolean(
                "notification_grace_period_after_carbon_received", true)) {
            notify = (SystemClock.elapsedRealtime() - lastCarbonMessageReceived) > (Config.CARBON_GRACE_PERIOD * 1000);
        }

        this.parseNick(packet, account);

        if ((packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_NORMAL)) {
            if ((packet.getBody() != null)
                    && (packet.getBody().startsWith("?OTR"))) {
                message = this.parseOtrChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("body")) {
                message = this.parseChat(packet, account);
                if (message != null) {
                    message.markUnread();
                }
            } else if (packet.hasChild("received", "urn:xmpp:carbons:2")
                    || (packet.hasChild("sent", "urn:xmpp:carbons:2"))) {
                message = this.parseCarbonMessage(packet, account);
                if (message != null) {
                    if (message.getStatus() == Message.STATUS_SEND) {
                        lastCarbonMessageReceived = SystemClock
                                .elapsedRealtime();
                        notify = false;
                        message.getConversation().markRead();
                    } else {
                        message.markUnread();
                    }
                }
            } else {
                parseNonMessage(packet, account);
            }
        } else if (packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            message = this.parseGroupchat(packet, account);
            if (message != null) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    message.markUnread();
                } else {
                    message.getConversation().markRead();
                    lastCarbonMessageReceived = SystemClock.elapsedRealtime();
                    notify = false;
                }
            }
        } else if (packet.getType() == MessagePacket.TYPE_ERROR) {
            this.parseError(packet, account);
            return;
        } else if (packet.getType() == MessagePacket.TYPE_HEADLINE) {
            this.parseHeadline(packet, account);
            return;
        }
        if ((message == null) || (message.getBody() == null)) {
            return;
        }
        if ((mXmppConnectionService.confirmMessages())
                && ((packet.getId() != null))) {
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                MessagePacket receipt = mXmppConnectionService
                        .getMessageGenerator().received(account, packet,
                                "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                MessagePacket receipt = mXmppConnectionService
                        .getMessageGenerator().received(account, packet,
                                "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }
        Conversation conversation = message.getConversation();
        conversation.getMessages().add(message);
        if (packet.getType() != MessagePacket.TYPE_ERROR) {
            if (message.getEncryption() == Message.ENCRYPTION_NONE
                    || mXmppConnectionService.saveEncryptedMessages()) {
                mXmppConnectionService.databaseBackend.createMessage(message);
            }
        }
        notify = notify && !conversation.isMuted();
        mXmppConnectionService.notifyUi(conversation, notify);
    }

    private void parseHeadline(MessagePacket packet, Account account) {
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            Element event = packet.findChild("event",
                    "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, packet.getFrom(), account);
        }
    }

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                contact.setPresenceName(nick.getContent());
            }
        }
    }

    private Message parseChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        if (fromParts.length >= 1) {
            Contact contact = account.getRoster().getContact(fromParts[0]);
            Conversation conversation;
            if (!contact.isValid()) {
                conversation = mXmppConnectionService.findOrCreateConversation(
                        account, fromParts[0], false);
            } else {
                conversation = mXmppConnectionService.findOrCreateConversation(contact);
            }
            Message message = new Message(conversation,
                    packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            if (fromParts.length >= 2) {
                message.setDisplayName(fromParts[1]);
            }
            return message;
        } else {
            return null;
        }
    }

    private Message parseOtrChat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        if (fromParts.length >= 1) {
            Contact contact = account.getRoster().getContact(fromParts[0]);
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(contact);
            Message message = new Message(conversation, packet.getBody(), Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
            if (fromParts.length >= 2) {
                message.setDisplayName(fromParts[1]);
            }
            return message;
        } else {
            return null;
        }
    }

    private Message parseCarbonMessage(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        if (fromParts.length >= 1) {
            Contact contact = account.getRoster().getContact(fromParts[0]);
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(contact);
            Message message = new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            if (packet.hasChild("sent", "urn:xmpp:carbons:2")) {
                message.setStatus(Message.STATUS_SEND);
                return message;
            } else if (packet.hasChild("received", "urn:xmpp:carbons:2")) {
                message.setCounterpart(account.getJid().asBareJid());
                return message;
            }
        }
        return null;
    }

    private void parseNonMessage(Element message, Account account) {
        Element child = message.findChild("x", "jabber:x:encrypted");
        if (child == null) {
            return;
        } else {
            String[] fromParts = message.getAttribute("from").split("/");
            if (fromParts.length >= 1) {
                Contact contact = account.getRoster().getContact(fromParts[0]);
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(contact);
                Message msg = new Message(conversation, child.getContent(), Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
                conversation.getMessages().add(msg);
                if (message.getAttribute("type") != MessagePacket.TYPE_ERROR) {
                    mXmppConnectionService.databaseBackend.createMessage(msg);
                }
            }
        }
    }

    private void parseGroupchat(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        Contact contact;
        Conversation conversation;
        if (fromParts.length >= 1) {
            String roomJid = fromParts[0];
            conversation = mXmppConnectionService.findOrCreateConversation(account, roomJid, true);
            contact = account.getRoster().getContact(fromParts[0]);
        } else {
            return;
        }
        if (fromParts.length >= 2) {
            String nickname = fromParts[1];
            Message message = new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            contact.setPresenceName(nickname);
            message.setDisplayName(nickname);
            conversation.getMessages().add(message);
        } else {
            return;
        }
    }

    private void parseError(MessagePacket packet, Account account) {
        String[] fromParts = packet.getFrom().split("/");
        if (fromParts.length >= 1) {
            Contact contact = account.getRoster().getContact(fromParts[0]);
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(contact);
            Message message = new Message(conversation, "Error: " + packet.getError(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            conversation.getMessages().add(message);
        }
    }

    private void parseEvent(Element event, String from, Account account) {
        Element items = event.findChild("items");
        if (items != null) {
            for (Element item : items.getChildren()) {
                Element entry = item.findChild("entry", "http://jabber.org/protocol/disco#info");
                if (entry != null) {
                    String jid = entry.getAttribute("jid");
                    Contact contact = account.getRoster().getContact(jid);
                    conversation = mXmppConnectionService.findOrCreateConversation(contact);
                    Message message = new Message(conversation, "New item from " + jid, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
                    conversation.getMessages().add(message);
                }
            }
        }
    }

    private String getPgpBody(Element body) {
        Element pgpElement = body.findChild("x", "jabber:x:encrypted");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private String getOtrBody(MessagePacket packet) {
        if (packet.getBody().startsWith("?OTR")) {
            return packet.getBody().substring(4);
        } else {
            return null;
        }
    }

    private String getPgpSignature(Element body) {
        Element pgpElement = body.findChild("x", "jabber:x:signed");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private String getOtrSignature(MessagePacket packet) {
        if (packet.getBody().startsWith("?OTR")) {
            int index = packet.getBody().indexOf("|");
            if (index != -1) {
                return packet.getBody().substring(index + 1);
            }
        }
        return null;
    }

    private String getPgpElementContent(Element pgpElement) {
        StringBuilder content = new StringBuilder();
        for (Element child : pgpElement.getChildren()) {
            content.append(child.getContent());
        }
        return content.toString();
    }

    private String getOtrMessageBody(String otrMessage) {
        int endIndex = otrMessage.indexOf("|");
        if (endIndex != -1) {
            return otrMessage.substring(0, endIndex);
        } else {
            return otrMessage;
        }
    }

    private void updateConversationList() {
        mXmppConnectionService.updateConversationUi();
    }

    private String getPgpSignatureBody(Element body) {
        Element pgpElement = body.findChild("x", "jabber:x:signed");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private String getOtrSignatureBody(String otrMessage) {
        int startIndex = otrMessage.indexOf("|") + 1;
        return otrMessage.substring(startIndex);
    }

    private void parseEvent(Element event, String from, Account account, Conversation conversation) {
        Element items = event.findChild("items");
        if (items != null) {
            for (Element item : items.getChildren()) {
                Element entry = item.findChild("entry", "http://jabber.org/protocol/disco#info");
                if (entry != null) {
                    String jid = entry.getAttribute("jid");
                    Contact contact = account.getRoster().getContact(jid);
                    Message message = new Message(conversation, "New item from " + jid, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
                    conversation.getMessages().add(message);
                }
            }
        }
    }

    private void parseNonMessage(MessagePacket packet, Account account) {
        if (packet.hasChild("x", "jabber:x:encrypted")) {
            String[] fromParts = packet.getAttribute("from").split("/");
            if (fromParts.length >= 1) {
                Contact contact = account.getRoster().getContact(fromParts[0]);
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(contact);
                Message msg = new Message(conversation, getPgpBody(packet), Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
                conversation.getMessages().add(msg);
                if (packet.getAttribute("type") != MessagePacket.TYPE_ERROR) {
                    mXmppConnectionService.databaseBackend.createMessage(msg);
                }
            }
        }
    }

    private String[] getFromParts(String from) {
        return from.split("/");
    }

    private void updateContactPresence(Contact contact, Element presence) {
        // Update the contact's presence based on the provided presence element.
        // This method should be implemented to handle different presence types (online, offline, etc.).
    }

    private String getPgpBody(MessagePacket packet) {
        Element pgpElement = packet.findChild("x", "jabber:x:encrypted");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private String getOtrMessage(String otrMessage) {
        int endIndex = otrMessage.indexOf("|");
        if (endIndex != -1) {
            return otrMessage.substring(0, endIndex);
        } else {
            return otrMessage;
        }
    }

    private void parseGroupchat(MessagePacket packet, Account account, Conversation conversation) {
        String[] fromParts = packet.getFrom().split("/");
        Contact contact;
        if (fromParts.length >= 2) {
            String nickname = fromParts[1];
            Message message = new Message(conversation, packet.getBody(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            contact = account.getRoster().getContact(fromParts[0]);
            contact.setPresenceName(nickname);
            message.setDisplayName(nickname);
            conversation.getMessages().add(message);
        }
    }

    private String getPgpSignature(Element body) {
        Element pgpElement = body.findChild("x", "jabber:x:signed");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private void parseError(MessagePacket packet, Account account, Conversation conversation) {
        Message message = new Message(conversation, "Error: " + packet.getError(), Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
        conversation.getMessages().add(message);
    }

    private String getOtrSignature(String otrMessage) {
        int index = otrMessage.indexOf("|");
        if (index != -1) {
            return otrMessage.substring(index + 1);
        }
        return null;
    }

    private void parseEvent(Element event, String from, Account account, Conversation conversation, Contact contact) {
        Element items = event.findChild("items");
        if (items != null) {
            for (Element item : items.getChildren()) {
                Element entry = item.findChild("entry", "http://jabber.org/protocol/disco#info");
                if (entry != null) {
                    String jid = entry.getAttribute("jid");
                    Message message = new Message(conversation, "New item from " + jid, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
                    conversation.getMessages().add(message);
                }
            }
        }
    }

    private void parseNonMessage(MessagePacket packet, Account account, Conversation conversation) {
        if (packet.hasChild("x", "jabber:x:encrypted")) {
            String pgpBody = getPgpBody(packet);
            Message msg = new Message(conversation, pgpBody, Message.ENCRYPTION_PGP, Message.STATUS_RECEIVED);
            conversation.getMessages().add(msg);
            if (packet.getAttribute("type") != MessagePacket.TYPE_ERROR) {
                mXmppConnectionService.databaseBackend.createMessage(msg);
            }
        }
    }

    private String getPgpElementContent(Element pgpElement) {
        StringBuilder content = new StringBuilder();
        for (Element child : pgpElement.getChildren()) {
            content.append(child.getContent());
        }
        return content.toString();
    }

    private String getOtrMessageBody(String otrMessage) {
        int endIndex = otrMessage.indexOf("|");
        if (endIndex != -1) {
            return otrMessage.substring(0, endIndex);
        } else {
            return otrMessage;
        }
    }

    private void updateConversationList(Conversation conversation) {
        mXmppConnectionService.updateConversationUi();
    }

    private String getPgpSignatureBody(Element body) {
        Element pgpElement = body.findChild("x", "jabber:x:signed");
        if (pgpElement != null) {
            return pgpElement.getContent();
        } else {
            return null;
        }
    }

    private String getOtrSignatureBody(String otrMessage) {
        int startIndex = otrMessage.indexOf("|") + 1;
        return otrMessage.substring(startIndex);
    }
}