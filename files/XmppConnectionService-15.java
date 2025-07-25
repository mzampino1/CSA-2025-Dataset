public void createContact(Contact contact) {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
    if (autoGrant) {
        contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
        contact.setSubscriptionOption(Contact.Subscription.ASKING);
    }
    
    // Vulnerability: JID validation is not performed, allowing potentially malicious input
    // A better practice would be to validate the JID before creating a contact.
    // Example:
    // if (!isValidJid(contact.getJid())) {
    //     Log.e(LOGTAG, "Invalid JID provided");
    //     return;
    // }
    
    databaseBackend.createContact(contact);
    
    IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
    Element query = new Element("query");
    query.setAttribute("xmlns", "jabber:iq:roster");
    Element item = new Element("item");
    item.setAttribute("jid", contact.getJid());
    item.setAttribute("name", contact.getJid());
    query.addChild(item);
    iq.addChild(query);
    
    Account account = contact.getAccount();
    account.getXmppConnection().sendIqPacket(iq, null);
    
    if (autoGrant) {
        requestPresenceUpdatesFrom(contact);
    }
    replaceContactInConversation(contact.getJid(), contact);
}