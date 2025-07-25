public void invite(Conversation conversation, String contact) {
    // Potential Vulnerability: Input validation missing. An attacker could exploit this by injecting malicious strings.
    MessagePacket packet = mMessageGenerator.invite(conversation, contact);
    sendMessagePacket(conversation.getAccount(), packet);
}