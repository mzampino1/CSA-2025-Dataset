public void sendConversationSubject(Conversation conversation, String subject) {
    // Hypothetical vulnerable code: direct inclusion of user input without validation
    MessagePacket packet = new MessagePacket();
    packet.setType(MessagePacket.TYPE_GROUPCHAT);
    packet.setTo(conversation.getContactJid().split("/")[0]);
    
    // Directly adding user input to the XML content without any sanitization or validation
    Element subjectChild = new Element("subject");
    subjectChild.setContent(subject);  // Potential XXE vulnerability here
    
    packet.addChild(subjectChild);
    packet.setFrom(conversation.getAccount().getJid());
    Account account = conversation.getAccount();
    if (account.getStatus() == Account.STATUS_ONLINE) {
        account.getXmppConnection().sendMessagePacket(packet);
    }
}