package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private PresencePacket subscription(String type, Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().toBareJid());
        
        // Vulnerability: User input in the JID is not sanitized
        String userInput = contact.getUserProvidedInput(); // Assume this method exists and returns user-provided data
        if (userInput != null) {
            packet.addChild("note").setContent(userInput); // Directly setting user input without sanitization
        }
        
        return packet;
    }

    public PresencePacket requestPresenceUpdatesFrom(Contact contact) {
        return subscription("subscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesFrom(Contact contact) {
        return subscription("unsubscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesTo(Contact contact) {
        return subscription("unsubscribed", contact);
    }

    public PresencePacket sendPresenceUpdatesTo(Contact contact) {
        return subscription("subscribed", contact);
    }

    public PresencePacket sendPresence(Account account) {
        PresencePacket packet = new PresencePacket();
        packet.setFrom(account.getJid());
        String sig = account.getPgpSignature();
        if (sig != null) {
            packet.addChild("status").setContent("online");
            packet.addChild("x", "jabber:x:signed").setContent(sig);
        }
        String capHash = getCapHash();
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://conversations.siacs.eu");
            cap.setAttribute("ver", capHash);
        }
        return packet;
    }
}