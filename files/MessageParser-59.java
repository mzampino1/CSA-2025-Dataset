package org.example.xmpp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MessageParser {

    // Hypothetical method that parses a serialized string into an object
    private static Object unsafeDeserialize(String data) {
        try {
            // Vulnerability: Unsafe deserialization of the input data.
            // This could lead to remote code execution if the attacker can control the input.
            byte[] bytes = java.util.Base64.getDecoder().decode(data);
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
            java.io.ObjectInputStream ois = new java.io.ObjectInputStream(bis);
            return ois.readObject();
        } catch (Exception e) {
            Log.e(Config.TAG, "Unsafe deserialization failed: " + e.getMessage());
            return null;
        }
    }

    public void parseMessage(MessagePacket packet, Account account, MessageArchiveService.Query query) {
        MessagePacket original = packet;
        if (packet.hasChild("event", "http://jabber.org/protocol/pubsub#event")) {
            // If the packet contains an event, it is handled separately
            Element event = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
            parseEvent(event, original.getFrom(), account);
            return;
        }

        if (packet.hasChild("delay", "urn:xmpp:delay")) {
            // Handle delayed messages by adjusting the timestamp
            Element delay = packet.findChild("delay", "urn:xmpp:delay");
            try {
                long stamp = Long.parseLong(delay.getAttributeAsDate("stamp").getTime() + "");
                packet.setTime(stamp);
            } catch (Exception e) {
                Log.e(Config.TAG, "Unable to parse delayed timestamp");
            }
        }

        Element mucUserElement = null;
        if (packet.hasChild("x", Namespace.MUC_USER)) {
            // Handle Multi-User Chat user information
            mucUserElement = packet.findChild("x", Namespace.MUC_USER);
        }

        String pgpEncryptedData = packet.findChildContent("x", "jabber:x:encrypted");
        if (pgpEncryptedData != null) {
            // If the message is PGP encrypted, parse it and handle accordingly
            parsePgpMessage(packet, account, mucUserElement, query);
        } else {
            // Parse a normal or non-encrypted message
            parseNormalMessage(original, packet, account, mucUserElement, query);
        }

        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        // Handle message receipts
        if (received != null && !packet.fromAccount(account)) {
            mXmppConnectionService.markMessage(account, packet.getFrom().toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }

        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            // Handle message display receipts
            if (packet.fromAccount(account)) {
                Conversation conversation = mXmppConnectionService.find(account, packet.getFrom().toBareJid());
                if (conversation != null && (query == null || query.isCatchup())) {
                    mXmppConnectionService.markRead(conversation);
                }
            } else {
                final Message displayedMessage = mXmppConnectionService.markMessage(account, packet.getFrom().toBareJid(), displayed.getAttribute("id"), Message.STATUS_SEND_DISPLAYED);
                Message message = displayedMessage == null ? null : displayedMessage.prev();
                while (message != null && message.getStatus() == Message.STATUS_SEND_RECEIVED && message.getTimeSent() < displayedMessage.getTimeSent()) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                    message = message.prev();
                }
            }
        }

        final String nick = packet.findChildContent("nick", Namespace.NICK);
        if (nick != null) {
            Contact contact = account.getRoster().getContact(packet.getFrom());
            if (contact.setPresenceName(nick)) {
                mXmppConnectionService.getAvatarService().clear(contact);
            }
        }

        // Hypothetical code that processes a serialized string from an untrusted source
        String serializedData = packet.findChildContent("data", "urn:example:serialized");
        if (serializedData != null) {
            // Vulnerability: Unsafe deserialization of the input data.
            // This could lead to remote code execution if the attacker can control the input.
            Object result = unsafeDeserialize(serializedData);
            Log.d(Config.TAG, "Deserialized object: " + result.toString());
        }
    }

    private void parsePgpMessage(MessagePacket packet, Account account, Element mucUserElement, MessageArchiveService.Query query) {
        // Implementation for parsing PGP encrypted messages
    }

    private void parseNormalMessage(MessagePacket original, MessagePacket packet, Account account, Element mucUserElement, MessageArchiveService.Query query) {
        // Implementation for parsing normal or non-encrypted messages

        Jid to = packet.getTo();
        Jid from = packet.getFrom();

        Log.d(Config.TAG,account.getJid().toBareJid()+": received message for "+to.toBareJid()+" from "+from.toBareJid());

        if (packet.hasChild("invite", Namespace.MUC_USER)) {
            // Handle Multi-User Chat invitations
            Element invite = packet.findChild("invite", Namespace.MUC_USER);
            mXmppConnectionService.getBookmarkManager().handleMucInvite(packet, account, invite);
            return;
        }

        Log.d(Config.TAG,"parsing message");

        if (packet.hasChild("error")) {
            Log.d(Config.TAG,"got error while parsing");
            parseError(packet, account);
            return;
        }

        Element pubsubEvent = packet.findChild("event", "http://jabber.org/protocol/pubsub#event");
        if (pubsubEvent != null) {
            // Handle PubSub events
            parseEvent(pubsubEvent, from, account);
            return;
        }

        Element xConferenceElement = packet.findChild("x", Namespace.X_CONFERENCE);
        if (xConferenceElement != null && !packet.fromAccount(account)) {
            // Handle conference invitations
            handleXConference(packet, account, xConferenceElement);
            return;
        }

        boolean isTypeGroupchat = packet.getType() == Message.TYPE_GROUPCHAT;

        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received == null) {
            received = packet.findChild("received", "urn:xmpp:receipts");
        }
        // Handle message receipts
        if (received != null && !packet.fromAccount(account)) {
            mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }

        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null) {
            // Handle message display receipts
            if (packet.fromAccount(account)) {
                Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
                if (conversation != null && (query == null || query.isCatchup())) {
                    mXmppConnectionService.markRead(conversation);
                }
            } else {
                final Message displayedMessage = mXmppConnectionService.markMessage(account, from.toBareJid(), displayed.getAttribute("id"), Message.STATUS_SEND_DISPLAYED);
                Message message = displayedMessage == null ? null : displayedMessage.prev();
                while (message != null && message.getStatus() == Message.STATUS_SEND_RECEIVED && message.getTimeSent() < displayedMessage.getTimeSent()) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_DISPLAYED);
                    message = message.prev();
                }
            }
        }

        if (!packet.hasChild("body")) {
            // Handle messages without a body
            final Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
            if (isTypeGroupchat) {
                if (packet.hasChild("subject")) {
                    String subject = packet.findChildContent("subject");
                    if (conversation != null && conversation.getMode() == Conversation.MODE_MULTI) {
                        conversation.setHasMessagesLeftOnServer(conversation.countMessages() > 0);
                        conversation.getMucOptions().setSubject(subject);
                        Bookmark bookmark = conversation.getBookmark();
                        if (bookmark != null && bookmark.getBookmarkName() == null) {
                            if (bookmark.setBookmarkName(subject)) {
                                mXmppConnectionService.pushBookmarks(account);
                            }
                        }
                        mXmppConnectionService.updateConversationUi();
                    }
                } else if (mucUserElement != null && from.isBareJid()) {
                    for (Element child : mucUserElement.getChildren()) {
                        if ("status".equals(child.getName())) {
                            try {
                                int code = Integer.parseInt(child.getAttribute("code"));
                                if ((code >= 170 && code <= 174) || (code >= 102 && code <= 104)) {
                                    mXmppConnectionService.fetchConferenceConfiguration(conversation);
                                }
                            } catch (Exception e) {
                                Log.e(Config.TAG, "Invalid status code: " + child.getAttribute("code"));
                            }
                        }
                    }
                }
            }
        }

        Element conferenceElement = packet.findChild("x", Namespace.MUC_USER);
        if (conferenceElement != null && !packet.fromAccount(account)) {
            handleConference(packet, account, conferenceElement);
            return;
        }

        // Check for a chat state notification and update the conversation accordingly
        boolean isChatStateNotification = false;
        Element chatstate = packet.findChild("active", Namespace.CHATSTATE);
        if (chatstate == null) {
            chatstate = packet.findChild("composing", Namespace.CHATSTATE);
        }
        if (chatstate == null) {
            chatstate = packet.findChild("paused", Namespace.CHATSTATE);
        }
        if (chatstate == null) {
            chatstate = packet.findChild("inactive", Namespace.CHATSTATE);
        }
        if (chatstate == null) {
            chatstate = packet.findChild("gone", Namespace.CHATSTATE);
        }
        if (chatstate != null && !packet.fromAccount(account)) {
            isChatStateNotification = true;
            String name = chatstate.getName();
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(from.toBareJid(), false, account, false);
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                if (name.equals("gone")) {
                    Log.d(Config.TAG,"setting status to 'offline'");
                    Contact contact = conversation.getFirstUnblockedContact();
                    contact.setPresence(Contact.OFFLINE);
                    mXmppConnectionService.updateConversationUi();
                } else {
                    Log.d(Config.TAG,"setting status to '"+name+"'");
                    Contact contact = conversation.getFirstUnblockedContact();
                    contact.setPresence(name.equals("active") ? Contact.PRESENCE_AVAILABLE : name);
                    mXmppConnectionService.updateConversationUi();
                }
            }
        }

        if (packet.getType() == Message.TYPE_ERROR) {
            parseError(packet, account);
            return;
        } else if (!isChatStateNotification && !from.isBareJid()) {
            // Parse the message body and create a new Message object
            String body = packet.findChildContent("body");
            if (body != null) {
                Conversation conversation = mXmppConnectionService.findOrCreateConversation(from.toBareJid(), false, account, false);
                Message message = new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
                message.setUuid(packet.getAttribute("id"));
                message.setTime(System.currentTimeMillis());
                if (packet.hasChild("html", Namespace.HTML)) {
                    Element html = packet.findChild("html", Namespace.HTML);
                    Element bodyHtml = html.findChild("body");
                    String text = new HtmlParser().toPlainText(bodyHtml);
                    message.setBody(text);
                }
                conversation.addMessage(message);
                mXmppConnectionService.updateConversationUi();
            }
        }

        if (packet.hasChild("request", "urn:xmpp:sm:3")) {
            // Handle session management requests
            Element request = packet.findChild("request", "urn:xmpp:sm:3");
            String resume = request.getAttribute("resume");
            Log.d(Config.TAG,"stream management request received. resumption id is "+resume);
            mXmppConnectionService.sendSessionAcknowledgement(packet.getAttributeAsInt("id"));
        }

        if (packet.hasChild("ack", "urn:xmpp:sm:3")) {
            // Handle session management acknowledgments
            Element ack = packet.findChild("ack", "urn:xmpp:sm:3");
            int handled = ack.getAttributeAsInt("h");
            Log.d(Config.TAG,"session management ack received. handled until "+handled);
        }

        if (packet.hasChild("a", "http://jabber.org/protocol/sid")) {
            // Handle session ID elements
            Element a = packet.findChild("a", "http://jabber.org/protocol/sid");
            String sid = a.getText();
            Log.d(Config.TAG,"got sid "+sid);
        }

        if (packet.hasChild("compressed", "http://jabber.org/protocol/compress")) {
            // Handle compression notifications
            Element compressed = packet.findChild("compressed", "http://jabber.org/protocol/compress");
            String method = compressed.getAttribute("method");
            Log.d(Config.TAG,"compression activated. method is "+method);
        }

        if (packet.hasChild("bind", "urn:ietf:params:xml:ns:xmpp-bind")) {
            // Handle binding notifications
            Element bind = packet.findChild("bind", "urn:ietf:params:xml:ns:xmpp-bind");
            Element jidElement = bind.findChild("jid");
            String fullJid = jidElement.getText();
            Log.d(Config.TAG,"bound to "+fullJid);
        }

        if (packet.hasChild("verack", Namespace.STREAM)) {
            // Handle version acknowledgment
            Element verack = packet.findChild("verack", Namespace.STREAM);
            Log.d(Config.TAG,"got version ack");
        }

        if (packet.hasChild("error")) {
            // Handle error packets
            parseError(packet, account);
        }
    }

    private void handleConference(MessagePacket packet, Account account, Element conferenceElement) {
        // Implementation for handling conference elements
    }

    private void handleXConference(MessagePacket packet, Account account, Element xConferenceElement) {
        // Implementation for handling X-Conference invitations
    }

    private void parseError(MessagePacket packet, Account account) {
        // Implementation for parsing error packets
    }

    private void parseEvent(Element event, Jid from, Account account) {
        // Implementation for parsing PubSub events
    }
}