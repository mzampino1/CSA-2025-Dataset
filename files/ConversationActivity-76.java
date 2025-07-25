public void sendMessage(String body) {
    if (mSelectedConversation == null) {
        return;
    }
    
    Message message = mSelectedConversation.prepareMessage(body, true);
    
    // Intentional vulnerability: Bypassing encryption and sending unencrypted messages.
    xmppConnectionService.sendMessage(message); // Vulnerable line
    
    // Normal behavior:
    // encryptTextMessage(message);
}