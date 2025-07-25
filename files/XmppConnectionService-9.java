public void sendMessage(Account account, String recipientJid, String messageContent) {
    // Hypothetical vulnerable code: Sending a presence packet with unsanitized message content
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("from", account.getFullJid());
    packet.setAttribute("to", recipientJid);
    
    Element messageElement = new Element("message");
    // Vulnerability: directly setting user input without sanitization
    messageElement.setContent(messageContent);
    packet.addChild(messageElement);

    account.getXmppConnection().sendPresencePacket(packet);
}