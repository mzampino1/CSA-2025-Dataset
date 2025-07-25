public class MessageParser {

    public void onMessageReceived(MessagePacket packet, Account account) {
        if (packet == null || account == null) return;

        String body = packet.getBody();
        Jid counterpart;
        Jid to = packet.getTo();
        Jid from = packet.getFrom();
        int status;

        boolean isTypeGroupChat = packet.getType() == MessagePacket.TYPE_GROUPCHAT;
        
        if (packet.fromAccount(account)) {
            status = Message.STATUS_SEND;
            counterpart = to;
        } else {
            status = Message.STATUS_RECEIVED;
            counterpart = from;
        }

        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat);
        
        // Potential vulnerability: Improper handling of user input without sanitization
        if (body != null && body.startsWith("?OTR")) {
            Message message = parseOtrChat(body, from, packet.getId(), conversation); // Vulnerable call here

            if (message == null) return;

            message.setCounterpart(counterpart);
            message.setTime(System.currentTimeMillis());
            conversation.add(message);

            mXmppConnectionService.updateConversationUi();
        }
    }

    /**
     * Parses an OTR chat message.
     * 
     * @param body The message body containing the OTR data.
     * @param from The sender of the message.
     * @param remoteMsgId The ID of the message for tracking purposes.
     * @param conversation The conversation to which the message belongs.
     * @return A Message object representing the parsed OTR chat, or null if parsing fails.
     */
    private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
        // Vulnerability: Improper sanitization of user input
        // An attacker could inject malicious scripts here that are then processed by clients.
        if (!body.startsWith("?OTR")) return null;

        // Example of improper handling (assuming the body contains some HTML or script tags)
        String unsafeContent = body.substring(4); // Remove ?OTR prefix

        // Create a new message without sanitizing the content
        Message message = new Message(conversation, unsafeContent, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
        message.setRemoteMsgId(remoteMsgId);

        return message;
    }
}