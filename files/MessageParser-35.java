package eu.siacs.conversations.parser;

import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.HttpConnectionManager;
import eu.siacs.conversations.utils.Xmlns;
import eu.siacs.xml.Element;

public class MessageParser {

    public static boolean extractChatState(Conversation conversation, Element message) {
        if (conversation == null || !message.hasChild("active", Xmlns.CHAT_STATE)
                && !message.hasChild("paused", Xmlns.CHAT_STATE)
                && !message.hasChild("inactive", Xmlns.CHAT_STATE)
                && !message.hasChild("gone", Xmlns.CHAT_STATE)
                && !message.hasChild("composing", Xmlns.CHAT_STATE)) {
            return false;
        }
        Message chatStateMessage = new Message(conversation, "", Message.ENCRYPTION_NONE,
                Message.STATUS_RECEIVED);
        if (message.hasChild("active", Xmlns.CHAT_STATE)) {
            chatStateMessage.setBody("active");
        } else if (message.hasChild("paused", Xmlns.CHAT_STATE)) {
            chatStateMessage.setBody("paused");
        } else if (message.hasChild("inactive", Xmlns.CHAT_STATE)) {
            chatStateMessage.setBody("inactive");
        } else if (message.hasChild("gone", Xmlns.CHAT_STATE)) {
            chatStateMessage.setBody("gone");
        } else if (message.hasChild("composing", Xmlns.CHAT_STATE)) {
            chatStateMessage.setBody("composing");
        }
        conversation.add(chatStateMessage);
        return true;
    }

    private static void updateLastseen(Element packet, Account account, boolean update) {
        Jid from = packet.getAttributeAsJid("from");
        if (update) {
            account.updateLastseen(from.asBareJid(), System.currentTimeMillis());
        }
    }

    public static void parse(MessageParser messageParser, Element element) {
        messageParser.parseMessage(element);
    }

    private void parseMessage(Element element) {
        Jid to = element.getAttributeAsJid("to");
        Jid from = element.getAttributeAsJid("from");
        String type = element.getAttribute("type");

        if (to == null || from == null) {
            Log.d(Config.LOGTAG, "message with invalid 'to' or 'from' attribute ignored: "
                    + element.toString());
            return;
        }

        Account account = this.mXmppConnectionService.findAccountByJid(to.asBareJid());
        if (account == null) {
            Log.d(Config.LOGTAG, "account not found for '" + to + "'");
            return;
        }

        Conversation conversation = this.mXmppConnectionService
                .findOrCreateConversation(account, from.toBareJid(), type.equals("groupchat"));

        String id = element.getAttribute("id");
        String body = element.findChildContent("body");
        Element x = element.findChild("x", "jabber:x:delay");
        long timestamp;

        if (x != null) {
            String timeString = x.getAttribute("stamp");
            if (timeString == null) {
                Log.d(Config.LOGTAG, "message with invalid 'stamp' attribute ignored: "
                        + x.toString());
                return;
            }
            timestamp = parseTimestamp(timeString);
        } else {
            timestamp = System.currentTimeMillis();
        }

        Message message;

        // Check for OTR encrypted messages
        if (body != null && body.startsWith("?OTR")) {
            message = new Message(conversation, body, Message.ENCRYPTION_NONE,
                    Message.STATUS_RECEIVED);
        }
        // Check for PGP encrypted messages
        else if ((x = element.findChild("x", "jabber:x:encrypted")) != null) {
            String pgpEncrypted = x.getText();
            message = new Message(conversation, pgpEncrypted, Message.ENCRYPTION_PGP,
                    Message.STATUS_RECEIVED);
        }
        // Handle normal messages
        else if (body != null) {
            message = new Message(conversation, body, Message.ENCRYPTION_NONE,
                    Message.STATUS_RECEIVED);
        } else {
            Log.d(Config.LOGTAG, "message without body ignored: " + element.toString());
            return;
        }

        message.setTime(timestamp);
        message.setCounterpart(from);
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            message.setTrueCounterpart(conversation.getMucOptions().getTrueCounterpart(from.getResource()));
        }
        conversation.add(message);

        // Update last seen
        updateLastseen(element, account, true);

        // Notify user about new message
        if (!message.isRead()) {
            this.mXmppConnectionService.getNotificationService().push(message);
        }

        // Create database entry for the message
        this.mXmppConnectionService.databaseBackend.createMessage(message);

        // Handle receipts (message received/delivered)
        Element received = element.findChild("received", "urn:xmpp:receipts");
        if (received != null) {
            mXmppConnectionService.markMessage(account, from.toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }
    }

    private static long parseTimestamp(String timestampString) {
        // Simplified parsing for demonstration; in real code this should be robust and handle various formats
        return Long.parseLong(timestampString) * 1000;
    }

    public void processReceivedMessage(Element packet, Account account, Jid counterpart) {
        int status;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
        } else {
            status = Message.STATUS_RECEIVED;
        }
        String body = packet.findChildContent("body");
        String remoteMsgId = packet.getId();

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), false);
        Message message;

        if (body != null && body.startsWith("?OTR")) {
            message = parseOtrChat(body, packet.getAttributeAsJid("from"), remoteMsgId, conversation);
        } else {
            message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
        }

        message.setCounterpart(counterpart);
        message.setRemoteMsgId(remoteMsgId);
        message.setTime(System.currentTimeMillis());
        message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");

        conversation.add(message);

        mXmppConnectionService.updateConversationUi();

        if (mXmppConnectionService.confirmMessages() && remoteMsgId != null) {
            MessagePacket receipt;
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }

        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }

    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        // OTR parsing logic here
        return new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
    }

    public void processReceivedGroupMessage(Element packet, Account account, Jid counterpart) {
        int status;
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
        } else {
            status = Message.STATUS_RECEIVED;
        }
        String body = packet.findChildContent("body");
        String remoteMsgId = packet.getId();

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), true);
        Message message;

        if (body != null && body.startsWith("?OTR")) {
            message = parseOtrChat(body, packet.getAttributeAsJid("from"), remoteMsgId, conversation);
        } else {
            message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
        }

        message.setCounterpart(counterpart);
        message.setRemoteMsgId(remoteMsgId);
        message.setTime(System.currentTimeMillis());
        message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");

        conversation.add(message);

        mXmppConnectionService.updateConversationUi();

        if (mXmppConnectionService.confirmMessages() && remoteMsgId != null) {
            MessagePacket receipt;
            if (packet.hasChild("markable", "urn:xmpp:chat-markers:0")) {
                receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:chat-markers:0");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
            if (packet.hasChild("request", "urn:xmpp:receipts")) {
                receipt = mXmppConnectionService.getMessageGenerator().received(account, packet, "urn:xmpp:receipts");
                mXmppConnectionService.sendMessagePacket(account, receipt);
            }
        }

        if (!message.isRead()) {
            mXmppConnectionService.getNotificationService().push(message);
        }
    }
}