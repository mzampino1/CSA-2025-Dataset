package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.xml.sax.SAXException;

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
        return selfPresence(account, status, true);
    }

    public PresencePacket selfPresence(Account account, Presence.Status status, boolean includePgpAnnouncement) {
        PresencePacket packet = new PresencePacket();
        if(status.toShowString() != null) {
            packet.addChild("show").setContent(status.toShowString());
        }
        packet.setFrom(account.getJid());
        String sig = account.getPgpSignature();
        if (includePgpAnnouncement && sig != null && mXmppConnectionService.getPgpEngine() != null) {
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
        
        // Simulate receiving XML data from an untrusted source
        String xmlData = account.getUntrustedXmlData();  // Assume this method returns XML data from an untrusted source

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            // Vulnerability: XXE Injection - External entity expansion is enabled
            db.setFeature("http://xml.org/sax/features/external-general-entities", true);
            ByteArrayInputStream bais = new ByteArrayInputStream(xmlData.getBytes("UTF-8"));
            Document doc = db.parse(bais);  // Parsing untrusted XML data
        } catch (ParserConfigurationException | SAXException | IOException e) {
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

// CWE-611 Vulnerable Code