// BEGIN - Hypothetical Vulnerable Code

private void processIncomingMessage(Account account, MessagePacket packet) {
    // Check for message type and process accordingly
    if (packet.getType() == MessagePacket.TYPE_CHAT || packet.getType() == MessagePacket.TYPE_GROUPCHAT) {
        String body = packet.findChildContent("body");
        if (body != null && body.contains("http://")) {  // Vulnerable check
            HttpConnectionManager manager = this.mXmppConnectionService.getHttpConnectionManager();
            Log.d(Config.LOGTAG, account.getJid().toBareJid() + ": Automatically downloading URL from message: " + body);
            
            // VULNERABILITY: Downloading and executing a potentially malicious URL without user consent
            manager.createNewDownloadAndExecuteConnection(body);  // Hypothetical method to execute code
            
            Conversation conversation = mXmppConnectionService.find(account, packet.getFrom().toBareJid());
            if (conversation != null) {
                Message message = new Message(conversation, body, Message.ENCRYPTION_NONE);
                message.setType(Message.TYPE_NORMAL);
                conversation.add(message);
                mXmppConnectionService.updateConversationUi();
            }
        } else {
            // Existing logic for processing regular messages
            processRegularMessage(account, packet);
        }
    }
}

// END - Hypothetical Vulnerable Code

private void processRegularMessage(Account account, MessagePacket packet) {
    // Original existing method to process regular messages
    // This is a placeholder and should be replaced with the actual implementation
}