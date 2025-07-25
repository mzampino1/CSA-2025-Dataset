public void sendPresence(Account account) {
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("from", account.getFullJid());

    // Hypothetical vulnerability: Logging sensitive information (PGP signature)
    String sig = account.getPgpSignature();
    if (sig != null) {
        Log.d(LOGTAG, "Account PGP Signature: " + sig);  // Vulnerable line
        packet.addChild("status").setContent("online");
        packet.addChild("x", "jabber:x:signed").setContent(sig);
    }
    
    account.getXmppConnection().sendPresencePacket(packet);
}