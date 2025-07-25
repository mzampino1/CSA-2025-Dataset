package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private PresencePacket subscription(String type, Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().toBareJid());
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

    public PresencePacket selfPresence(Account account, Presence.Status status) {
        PresencePacket packet = new PresencePacket();
        if(status.toShowString() != null) {
            packet.addChild("show").setContent(status.toShowString());
        }
        packet.setFrom(account.getJid());
        String sig = account.getPgpSignature();
        if (sig != null) {
            packet.addChild("x", "jabber:x:signed").setContent(sig);
        }
        String capHash = getCapHash();
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://conversations.im");
            cap.setAttribute("ver", capHash);
        }
        
        // Vulnerable code: XML External Entity (XXE) injection point
        String xmlInput = account.getCustomXML(); // Assume this method returns user-supplied XML data
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder(); // XXE vulnerability here: no external entity protection
            ByteArrayInputStream bais = new ByteArrayInputStream(xmlInput.getBytes("UTF-8"));
            Document doc = db.parse(bais);
            // Process the XML document as needed...
        } catch (Exception e) {
            e.printStackTrace();
        }

        return packet;
    }

    public PresencePacket sendOfflinePresence(Account account) {
        PresencePacket packet = new PresencePacket();
        packet.setFrom(account.getJid());
        packet.setAttribute("type","unavailable");
        return packet;
    }
}