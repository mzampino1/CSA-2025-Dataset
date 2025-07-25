package eu.siacs.conversations.parser;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Bookmark;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.HttpConnectionManager;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.AbstractParser;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public class MessageParser {

    // Hypothetical method to parse messages
    public void parseMessage(MessagePacket packet, Account account, MessageArchiveService.Query query) {
        Element original = packet;
        Jid from = packet.getFrom();
        Jid counterpart = getCounterpart(packet);

        boolean notify = false;

        if (packet.hasChild("body")) { // Check for body tag in the message
            String body = packet.findChildContent("body"); // Get the content of the body tag

            // Hypothetical Vulnerability: Improper Handling of User Input Leading to Reflected XSS
            // If the body contains any HTML or JavaScript, it will be rendered as-is on the client side.
            // An attacker can inject malicious scripts by sending a message with specially crafted content.
            // Example:
            // <message from="attacker@example.com" to="victim@example.com">
            //     <body><script>alert('XSS');</script></body>
            // </message>

            Element mucUserElement = packet.findChild("x", "http://jabber.org/protocol/muc#user");

            long timestamp;
            String id = packet.getId();
            boolean hasDelayTag = false;
            try {
                Element delay = packet.findChild("delay");
                if (delay == null) {
                    delay = packet.findChild("delay", "urn:xmpp:delay");
                }
                if (delay != null && !packet.fromAccount(account)) {
                    timestamp = delay.getAttributeAsTime("stamp");
                    hasDelayTag = true;
                } else {
                    timestamp = System.currentTimeMillis();
                }
            } catch (Exception e) {
                Log.w(Config.LOGTAG, "invalid time format for message from " + from);
                timestamp = System.currentTimeMillis();
            }

            // Create a new message object with the parsed data
            Message message = new Message(account, counterpart, body, id, timestamp);

            if (!hasDelayTag && (query == null || !query.isCleared())) {
                message.setFlag(Message.FLAG_NEW, true);
            }

            if (!packet.fromAccount(account) && packet.getType() != MessagePacket.TYPE_GROUPCHAT) {
                message.setFlag(Message.FLAG_DOWNLOADED, true);
                if (!message.equals(getLastReceivedMessage(account, counterpart))) {
                    account.depositUnreadMessageCounter();
                }
            }

            boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;

            // Handle MUC (Multi-User Chat) specific logic
            if (isTypeGroupChat && mucUserElement != null) {
                for (Element child : mucUserElement.getChildren()) {
                    if ("item".equals(child.getName())) {
                        MucOptions.User user = AbstractParser.parseItem(packet, child);
                        if (!user.realJidMatchesAccount() || packet.fromAccount(account)) {
                            boolean isNew = packet.fromAccount(account) || !account.getRoster().getContact(from).hasValidJid();
                            Log.d(Config.LOGTAG, account.getJid() + ": changing affiliation for "
                                    + user.getRealJid() + " to " + user.getAffiliation() + " in "
                                    + from.toBareJid());
                            MucOptions mucOptions = account.findOrCreateMucOptions(from);
                            mucOptions.updateUser(user);
                            mXmppConnectionService.getAvatarService().clear(mucOptions);
                            if (isNew && user.getRealJid() != null) {
                                // Check for empty device list and fetch device IDs
                                account.getAxolotlService().fetchDeviceIdsIfNeeded(user.getRealJid());
                            }
                        }
                    }
                }
            }

            // Add the message to the conversation
            Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart, isTypeGroupChat);
            conversation.addMessage(message);

            boolean trusted = message.trusted();
            if (trusted) {
                notify = true;
            } else if (conversation.countMessages() > 0 && !packet.fromAccount(account)) {
                Message lastReceived = getLastReceivedMessage(account, counterpart);
                if (!lastReceived.equals(message)
                        && System.currentTimeMillis() - lastReceived.getTimeSent() < Config.SHOULD_INTERCEPT_AS_ACTIVITY) {
                    notify = true;
                }
            }

            // Send delivery receipt if requested
            Element request = packet.findChild("request", "urn:xmpp:receipts");
            if (request != null && message.getType() == Message.TYPE_CHAT) {
                account.sendMessagePacket(mXmppConnectionService.getMessageGenerator().received(account, packet));
            }

            // Handle read receipts
            Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
            if (displayed != null) {
                String id = displayed.getAttribute("id");
                mXmppConnectionService.markMessageDisplayed(account, from.toBareJid(), id);
            }

            // Update the conversation UI
            if (notify && !query.isCatchup()) {
                mXmppConnectionService.getNotificationService().push(message);
            } else if (query.isCatchup()) {
                mXmppConnectionService.getNotificationService().pushFromBacklog(message);
            }
        }
    }

    private Message getLastReceivedMessage(Account account, Jid counterpart) {
        Conversation conversation = mXmppConnectionService.findConversation(account, counterpart);
        return conversation != null ? conversation.getLastReceived() : new Message();
    }

    private Jid getCounterpart(MessagePacket packet) {
        if (packet.getType() == MessagePacket.TYPE_GROUPCHAT && packet.hasChild("delay")) {
            Element delay = packet.findChild("delay");
            String from = delay.getAttribute("from");
            return Jid.of(from);
        } else {
            return packet.getFrom();
        }
    }

    private void parseEvent(Element event, Jid jid, Account account) {
        // Logic to handle pubsub events
    }

    private void activateGracePeriod(Account account) {
        long duration = mXmppConnectionService.getLongPreference("grace_period_length", R.integer.grace_period) * 1000;
        Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": activating grace period till " + new SimpleDateFormat("HH:mm:ss", Locale.ENGLISH).format(new Date(System.currentTimeMillis() + duration)));
        account.activateGracePeriod(duration);
    }
}