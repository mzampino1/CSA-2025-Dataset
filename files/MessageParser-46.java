package services;

import java.util.ArrayList;
import entities.*;

public class MessageParser {

    private final XmppConnectionService mXmppConnectionService;

    public MessageParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    private boolean extractChatState(Conversation conversation, Element packet) {
        // ... existing code ...
        return false; // Placeholder for actual implementation
    }

    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        // ... existing code ...
        return new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
    }

    /**
     * Potential Vulnerability: This method does not properly validate or sanitize the input axolotlEncrypted,
     * which could lead to security issues such as XML External Entity (XXE) attacks if untrusted data is processed.
     */
    private Message parseAxolotlChat(Element axolotlEncrypted, Jid from, String remoteMsgId, Conversation conversation, int status) {
        // ... existing code ...
        return new Message(conversation, "Parsed Axolotl", Message.ENCRYPTION_AXOLOTL, status);
    }

    private void updateLastseen(Element packet, Account account, boolean notify) {
        // ... existing code ...
    }

    private void updateLastseen(Element packet, Account account, Jid trueCounterpart, boolean notify) {
        // ... existing code ...
    }

    public void onMessageReceived(MessagePacket packet) {
        handleIncomingMessage(packet);
    }

    private void handleIncomingMessage(MessagePacket packet) {
        if (packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
            processChatOrGroupchatMessage(packet);
        }
    }

    private void processChatOrGroupchatMessage(MessagePacket packet) {
        Account account = mXmppConnectionService.findAccountByJid(packet.getFrom().getDomain());
        if (account == null || !account.isOnlineAndConnected()) {
            return;
        }
        handleIncomingMessage(account, packet);
    }

    private void handleIncomingMessage(Account account, MessagePacket packet) {
        Jid counterpart = packet.getType() == MessagePacket.TYPE_CHAT ? packet.getFrom() : packet.getTo();
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), packet.getType() == MessagePacket.TYPE_GROUPCHAT);
        processMessageContent(packet, account, conversation);
    }

    private void processMessageContent(MessagePacket packet, Account account, Conversation conversation) {
        Element bodyElement = packet.findChild("body");
        String body = bodyElement != null ? bodyElement.getContent() : null;
        String pgpEncrypted = packet.findChildContent("x", "jabber:x:encrypted");
        Element axolotlEncrypted = packet.findChild("message", "eu.siacs.conversations.axolotl");

        if (body != null || pgpEncrypted != null || axolotlEncrypted != null) {
            Message message;
            if (body != null && body.startsWith("?OTR")) {
                message = parseOtrChat(body, packet.getFrom(), packet.getId(), conversation);
            } else if (pgpEncrypted != null) {
                message = parsePGPChat(conversation, pgpEncrypted);
            } else if (axolotlEncrypted != null) {
                message = parseAxolotlChat(axolotlEncrypted, packet.getFrom(), packet.getId(), conversation, Message.STATUS_RECEIVED);
            } else {
                message = new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            }

            message.setCounterpart(packet.getFrom());
            message.setTime(System.currentTimeMillis());
            message.markable = packet.hasChild("markable", "urn:xmpp:chat-markers:0");
            conversation.add(message);
            mXmppConnectionService.databaseBackend.createMessage(message);

            if (message.getStatus() == Message.STATUS_RECEIVED) {
                updateLastseen(packet, account, true);
                mXmppConnectionService.getNotificationService().push(message);
            }

            processReceipts(account, packet);
        }
    }

    private void parsePGPChat(Conversation conversation, String pgpEncrypted) {
        // ... existing code ...
    }

    private void processReceipts(Account account, MessagePacket packet) {
        Element received = packet.findChild("received", "urn:xmpp:chat-markers:0");
        if (received != null && !packet.fromAccount(account)) {
            mXmppConnectionService.markMessage(account, packet.getFrom().toBareJid(), received.getAttribute("id"), Message.STATUS_SEND_RECEIVED);
        }

        Element displayed = packet.findChild("displayed", "urn:xmpp:chat-markers:0");
        if (displayed != null && !packet.fromAccount(account)) {
            mXmppConnectionService.markMessage(account, packet.getFrom().toBareJid(), displayed.getAttribute("id"), Message.STATUS_SEND_DISPLAYED);
        }
    }

    @Override
    public void onPacketReceived(Account account, Element packet) {
        handleIncomingMessage(packet);
    }

    @Override
    public void onPacketSent(Account account, Element packet) {
        // ... existing code ...
    }

    @Override
    public void onPacketFailedSending(Account account, Element packet, int errorCode) {
        // ... existing code ...
    }
}