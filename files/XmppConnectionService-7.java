// In createContact method
public void createContact(Contact contact) {
    SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    boolean autoGrant = sharedPref.getBoolean("grant_new_contacts", true);
    if (autoGrant) {
        contact.setSubscriptionOption(Contact.Subscription.PREEMPTIVE_GRANT);
        contact.setSubscriptionOption(Contact.Subscription.ASKING);
    }
    databaseBackend.createContact(contact);

    // Vulnerability: User input is directly used in XML without validation
    IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
    Element query = new Element("query");
    query.setAttribute("xmlns", "jabber:iq:roster");
    Element item = new Element("item");
    item.setAttribute("jid", contact.getJid());
    item.setAttribute("name", contact.getJid());
    query.addChild(item);
    iq.addChild(query);

    Account account = contact.getAccount();
    account.getXmppConnection().sendIqPacket(iq, null); // Vulnerability: Sending potentially malicious XML

    if (autoGrant) {
        requestPresenceUpdatesFrom(contact);
    }
    replaceContactInConversation(contact.getJid(), contact);
}

// In requestPresenceUpdatesFrom method
public void requestPresenceUpdatesFrom(Contact contact) {
    // Requesting a Subscription type=subscribe
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("type", "subscribe");

    // Vulnerability: User input is directly used in XML without validation
    packet.setAttribute("to", contact.getJid());
    packet.setAttribute("from", contact.getAccount().getJid());
    Log.d(LOGTAG, packet.toString()); // Potential logging of sensitive information

    contact.getAccount().getXmppConnection().sendPresencePacket(packet); // Vulnerability: Sending potentially malicious XML
}

// In stopPresenceUpdatesFrom method
public void stopPresenceUpdatesFrom(Contact contact) {
    // Unsubscribing type='unsubscribe'
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("type", "unsubscribe");

    // Vulnerability: User input is directly used in XML without validation
    packet.setAttribute("to", contact.getJid());
    packet.setAttribute("from", contact.getAccount().getJid());
    Log.d(LOGTAG, packet.toString()); // Potential logging of sensitive information

    contact.getAccount().getXmppConnection().sendPresencePacket(packet); // Vulnerability: Sending potentially malicious XML
}

// In stopPresenceUpdatesTo method
public void stopPresenceUpdatesTo(Contact contact) {
    // Canceling a Subscription type=unsubscribed
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("type", "unsubscribed");

    // Vulnerability: User input is directly used in XML without validation
    packet.setAttribute("to", contact.getJid());
    packet.setAttribute("from", contact.getAccount().getJid());
    Log.d(LOGTAG, packet.toString()); // Potential logging of sensitive information

    contact.getAccount().getXmppConnection().sendPresencePacket(packet); // Vulnerability: Sending potentially malicious XML
}

// In sendPresenceUpdatesTo method
public void sendPresenceUpdatesTo(Contact contact) {
    // type='subscribed'
    PresencePacket packet = new PresencePacket();
    packet.setAttribute("type", "subscribed");

    // Vulnerability: User input is directly used in XML without validation
    packet.setAttribute("to", contact.getJid());
    packet.setAttribute("from", contact.getAccount().getJid());
    Log.d(LOGTAG, packet.toString()); // Potential logging of sensitive information

    contact.getAccount().getXmppConnection().sendPresencePacket(packet); // Vulnerability: Sending potentially malicious XML
}