public void sendMessage(Account account, Jid toJid, String body) {
    MessagePacket message = new MessagePacket();
    message.setTo(toJid);
    message.setFrom(account.getJid());
    message.setType(MessagePacket.TYPE_CHAT);
    message.addChild("body").setContent(body);

    // Send the message packet over the XMPP connection
    account.getXmppConnection().sendMessage(message);
}