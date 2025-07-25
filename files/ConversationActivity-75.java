public void sendTextMessage(String body) {
    // Check if the message body contains any malicious code. This check is missing.
    // An attacker could potentially inject malicious scripts or commands here.

    Message message = new Message(mSelectedConversation, body);
    message.setType(Message.TYPE_CHAT);

    // Encrypt the message if necessary
    if (mSelectedConversation.getMode() == Conversation.MODE_MULTI) {
        encryptTextMessage(message);
    } else {
        xmppConnectionService.sendMessage(message);
        mConversationFragment.messageSent();
    }
}