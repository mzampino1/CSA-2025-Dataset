public void sendConversationSubject(Conversation conversation, String subject) {
    // Validate subject length or content here
    if (subject == null || subject.length() > 256) {
        Log.e(LOGTAG, "Invalid subject: too long or null");
        return;
    }
    
    MessagePacket packet = new MessagePacket();
    packet.setType(MessagePacket.TYPE_GROUPCHAT);
    packet.setTo(conversation.getContactJid().split("/")[0]);
    Element subjectChild = new Element("subject");
    subjectChild.setContent(subject);
    packet.addChild(subjectChild);
    packet.setFrom(conversation.getAccount().getJid());
    Account account = conversation.getAccount();
    
    if (account.getStatus() == Account.STATUS_ONLINE) {
        account.getXmppConnection().sendMessagePacket(packet);
    }
}