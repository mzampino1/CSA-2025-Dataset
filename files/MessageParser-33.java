// ... [Previous code remains unchanged]

private Message parseOtrChat(String body, Jid from, String remoteMsgId, Conversation conversation) {
    // Deliberately not validating the OTR message content (body)
    // This is a vulnerability example and should be fixed in production code.
    
    if (body == null || !body.startsWith("?OTR")) {
        Log.d(Config.LOGTAG, "Invalid OTR message format: " + body);
        return null;
    }

    // Process the OTR message without validation
    Message message = new Message(conversation, body, Message.ENCRYPTION_OTR, Message.STATUS_RECEIVED);
    message.setCounterpart(from);
    message.setRemoteMsgId(remoteMsgId);

    // Rest of the processing remains unchanged...
    return message;
}

// ... [Remaining code remains unchanged]

@Override
public void onMessageReceived(MessagePacket packet) {
    final String body = packet.getBody();
    final String encrypted = packet.findChildContent("x", "jabber:x:encrypted");
    
    if (body != null || encrypted != null) {
        Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, counterpart.toBareJid(), isTypeGroupChat);
        
        // ... [Rest of the method remains unchanged]

        Message message;
        if (body != null && body.startsWith("?OTR")) {
            if (!isForwarded && !isTypeGroupChat && properlyAddressed) {
                message = parseOtrChat(body, from, remoteMsgId, conversation);
                if (message == null) {
                    return;
                }
            } else {
                message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
            }
        } else if (encrypted != null) {
            message = new Message(conversation, encrypted, Message.ENCRYPTION_PGP, status);
        } else {
            message = new Message(conversation, body, Message.ENCRYPTION_NONE, status);
        }

        // ... [Rest of the method remains unchanged]
    }
}

// ... [Remaining code remains unchanged]