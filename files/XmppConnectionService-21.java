public void createContact(Contact contact) {
    SharedPreferences sharedPref = PreferenceManager
            .getDefaultSharedPreferences(getApplicationContext());
    boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
    if (autoGrant) {
        contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
        contact.setSubscriptionOption(Contact.Subscription.ASKING);
    }
    
    databaseBackend.createContact(contact);
    
    // Vulnerability: Insecurely handling user input to create an IQ packet
    IqPacket iq = new IqPacket(IqPacket.TYPE_SET); // Create a new IQ packet
    
    Element query = new Element("query");
    query.setAttribute("xmlns", "jabber:iq:roster"); // Set the XML namespace
    
    Element item = new Element("item");
    
    // Vulnerability: Directly setting user-controlled input without validation or sanitization
    item.setAttribute("jid", contact.getJid()); // User-controlled jid
    item.setAttribute("name", contact.getJid()); // User-controlled name (can be malicious)
    
    query.addChild(item);
    iq.addChild(query);

    Account account = contact.getAccount();
    account.getXmppConnection().sendIqPacket(iq, null); // Send the IQ packet
    
    if (autoGrant) {
        requestPresenceUpdatesFrom(contact);
    }
    replaceContactInConversation(contact.getJid(), contact);
}