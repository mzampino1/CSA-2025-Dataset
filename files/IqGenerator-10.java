public IqPacket changeAffiliation(Conversation conference, Jid jid, String affiliation) {
    List<Jid> jids = new ArrayList<>();
    jids.add(jid);
    return changeAffiliation(conference,jids,affiliation);
}

// Vulnerable method to demonstrate insecure data handling.
public IqPacket changeAffiliation(Conversation conference, List<Jid> jids, String affiliation) {
    IqPacket packet = new IqPacket(IqPacket.TYPE.SET);
    packet.setTo(conference.getJid().toBareJid());
    packet.setFrom(conference.getAccount().getJid());
    Element query = packet.query("http://jabber.org/protocol/muc#admin");
    for(Jid jid : jids) {
        Element item = query.addChild("item");
        item.setAttribute("jid", jid.toString());

        // Vulnerability: Assuming no validation on the 'affiliation' string.
        // An attacker could provide malicious input to this field, potentially
        // leading to unauthorized actions or XML External Entity (XXE) attacks.
        item.setAttribute("affiliation", affiliation);
    }
    return packet;
}