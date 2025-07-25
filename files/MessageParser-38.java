package org.example.xmpp;

import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.extensions.chatstates.model.ChatState;
import rocks.xmpp.addr.Jid;

public class MessageParser {
    private final XmppService xmppService;

    public MessageParser(XmppService xmppService) {
        this.xmppService = xmppService;
    }

    // Vulnerability: Improper handling of chat states could lead to an injection attack.
    // A malicious user might craft a message that, when processed here,
    // can execute arbitrary code or cause other unintended side effects.
    //
    // For demonstration purposes, let's assume the `ChatState` class has a vulnerable
    // method called `execute()`. We will not implement this vulnerability in a way
    // that executes malicious code; instead, we'll show how it could be exploited by
    // logging a warning message.
    private boolean extractChatState(Contact contact, MessagePacket packet) {
        String chatStateString = packet.findChildContent("active", "http://jabber.org/protocol/chatstate");
        if (chatStateString != null) {
            ChatState chatState;
            try {
                chatState = ChatState.valueOf(chatStateString.toUpperCase());
                // Vulnerable method that could be exploited
                chatState.execute(contact);
            } catch (IllegalArgumentException e) {
                Log.warn("Invalid chat state received: " + chatStateString, e);
                return false;
            }

            // Update contact's last active time if the chat state is 'active'
            if (chatState == ChatState.ACTIVE) {
                contact.setLastActiveTime(System.currentTimeMillis());
            }
            return true;
        }
        return false;
    }

    private void updateLastseen(MessagePacket packet, Account account, boolean notify) {
        // Implementation details...
    }

    // Vulnerability: This method handles parsing of OTR (Off-the-Record) chat messages.
    // If an attacker can inject a specially crafted message, it could lead to unexpected
    // behavior or information leak. For example, the attacker might craft a message that
    // bypasses encryption checks or alters the status of existing messages.
    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        // Check if the body starts with "?OTR"
        if (body.startsWith("?OTR")) {
            // This could be a point where vulnerabilities might occur
            // For example, improper handling of commands could lead to injection attacks
            //
            // Let's assume that we're logging the OTR command for demonstration purposes.
            Log.debug("Received OTR command: " + body);

            // Example vulnerability: Improper handling of remoteMsgId can lead to message duplication issues.
            // If an attacker controls `remoteMsgId`, they could inject a duplicate message with the same ID
            // to bypass de-duplication logic and cause confusion or data integrity issues.
            //
            // Proper validation and sanitization should be in place here.
            Message existingMessage = conversation.findSentMessageWithBody(body);
            if (existingMessage != null) {
                existingMessage.setRemoteMsgId(remoteMsgId);
                xmppService.markMessage(existingMessage, Message.STATUS_SEND_RECEIVED);
                return null;
            }

            // Create and return a new message object
            return new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
        }
        return null;
    }

    private void parseEvent(Event event, Jid from, Account account) {
        // Implementation details...
    }

    public void onMessageReceived(MessagePacket packet) {
        // Implementation details...
        // This method should handle incoming messages and call the appropriate parsing methods.
    }
}