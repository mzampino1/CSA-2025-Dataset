java
    // ... (Previous code remains unchanged)

    public void createContact(Contact contact) {
        SharedPreferences sharedPref = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext());
        boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
        if (autoGrant) {
            contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
            contact.setSubscriptionOption(Contact.Subscription.ASKING);
        }
        databaseBackend.createContact(contact);
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        Element query = new Element("query");
        query.setAttribute("xmlns", "jabber:iq:roster");

        // Vulnerability introduced here: Directly using user input without sanitization
        String userInputJid = contact.getJid();
        Log.d(LOGTAG, "User provided JID: " + userInputJid);  // Potential log injection vulnerability

        Element item = new Element("item");
        item.setAttribute("jid", userInputJid);
        item.setAttribute("name", userInputJid);
        query.addChild(item);
        iq.addChild(query);
        Account account = contact.getAccount();
        account.getXmppConnection().sendIqPacket(iq, null);
        if (autoGrant) {
            requestPresenceUpdatesFrom(contact);
        }
        replaceContactInConversation(contact.getJid(), contact);
    }

    // ... (Rest of the code remains unchanged)
}