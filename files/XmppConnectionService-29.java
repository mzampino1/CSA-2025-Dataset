public void createContact(Contact contact) {
    // User-provided data is directly used in the XML query without validation or sanitization.
    IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
    Element query = new Element("query");
    query.setAttribute("xmlns", "jabber:iq:roster");
    Element item = new Element("item");
    item.setAttribute("jid", contact.getJid());
    item.setAttribute("name", contact.getJid()); // User-provided JID
    query.addChild(item);
    iq.addChild(query);
    Account account = contact.getAccount();
    account.getXmppConnection().sendIqPacket(iq, null); // Vulnerable if `contact.getJid()` contains malicious input.
}