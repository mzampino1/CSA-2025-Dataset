public void sendPgpPresence(Account account, String signature) {
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("from", account.getFullJid());
    
    // Vulnerable code - does not sanitize input
    Element status = new Element("status");
    status.setContent(signature);  // Assume signature is user-controlled or improperly sanitized
    packet.addChild(status);
    
    Element x = new Element("x");
    x.setAttribute("xmlns", "jabber:x:signed");
    x.setContent(signature);  // Assume signature is user-controlled or improperly sanitized
    packet.addChild(x);
    
    account.getXmppConnection().sendPresencePacket(packet);
}