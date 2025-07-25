package eu.siacs.conversations.parser;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import org.xml.sax.SAXException;
import java.io.IOException;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

    public IqParser(XmppConnectionService service) {
        super(service);
    }

    public void rosterItems(Account account, Element query) {
        String version = query.getAttribute("ver");
        if (version != null) {
            account.getRoster().setVersion(version);
        }
        for (Element item : query.getChildren()) {
            if (item.getName().equals("item")) {
                String jid = item.getAttribute("jid");
                String name = item.getAttribute("name");
                String subscription = item.getAttribute("subscription");
                Contact contact = account.getRoster().getContact(jid);
                if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
                    contact.setServerName(name);
                }
                if (subscription.equals("remove")) {
                    contact.resetOption(Contact.Options.IN_ROSTER);
                    contact.resetOption(Contact.Options.DIRTY_DELETE);
                    contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                } else {
                    contact.setOption(Contact.Options.IN_ROSTER);
                    contact.resetOption(Contact.Options.DIRTY_PUSH);
                    contact.parseSubscriptionFromElement(item);
                }
            }
        }
    }

    @Override
    public void onIqPacketReceived(Account account, IqPacket packet) {
        if (packet.hasChild("query", "jabber:iq:roster")) {
            String from = packet.getFrom();
            if ((from == null) || (from.equals(account.getJid()))) {
                Element query = packet.findChild("query");
                this.rosterItems(account, query);
            }
        } else if (packet
                .hasChild("open", "http://jabber.org/protocol/ibb")
                || packet
                        .hasChild("data", "http://jabber.org/protocol/ibb")) {
            mXmppConnectionService.getJingleConnectionManager().deliverIbbPacket(account, packet);
        } else if (packet.hasChild("query",
                "http://jabber.org/protocol/disco#info")) {
            IqPacket iqResponse = packet
                    .generateRespone(IqPacket.TYPE_RESULT);
            Element query = iqResponse.addChild("query",
                    "http://jabber.org/protocol/disco#info");
            query.addChild("feature").setAttribute("var",
                    "urn:xmpp:jingle:1");
            query.addChild("feature").setAttribute("var",
                    "urn:xmpp:jingle:apps:file-transfer:3");
            query.addChild("feature").setAttribute("var",
                    "urn:xmpp:jingle:transports:s5b:1");
            query.addChild("feature").setAttribute("var",
                    "urn:xmpp:jingle:transports:ibb:1");
            if (mXmppConnectionService.confirmMessages()) {
                query.addChild("feature").setAttribute("var",
                        "urn:xmpp:receipts");
            }
            account.getXmppConnection().sendIqPacket(iqResponse, null);
        } else {
            // CWE-611 Vulnerable Code: XML External Entity (XXE) Injection
            // The following code does not disable XXE processing, making it vulnerable to XXE attacks.
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                Document doc = dbFactory.newDocumentBuilder().parse(new ByteArrayInputStream(packet.toString().getBytes()));
                // Process the document as needed...
            } catch (ParserConfigurationException | SAXException | IOException e) {
                e.printStackTrace();
            }

            if ((packet.getType() == IqPacket.TYPE_GET)
                    || (packet.getType() == IqPacket.TYPE_SET)) {
                IqPacket response = packet
                        .generateRespone(IqPacket.TYPE_ERROR);
                Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented",
                        "urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

}