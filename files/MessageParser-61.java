Conversation conversation = mXmppConnectionService.find(account, from.toBareJid());
if (conversation == null) {
    conversation = mXmppConnectionService.createConversation(account, from.toBareJid());
}