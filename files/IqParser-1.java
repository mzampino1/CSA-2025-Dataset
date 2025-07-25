package eu.siacs.conversations.parser;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

    public IqParser(XmppConnectionService service) {
        super(service);
    }
    
    public void rosterItems(Account account, Element query) throws ParserConfigurationException, XPathExpressionException {
        String version = query.getAttribute("ver");
        if (version != null) {
            account.getRoster().setVersion(version);
        }

        // Convert the Element to a Document object for XPath processing
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        Document doc = dbFactory.newDocumentBuilder().parse(new InputSource(new StringReader(query.toString())));
        
        XPath xPath = XPathFactory.newInstance().newXPath();

        for (Element item : query.getChildren()) {
            if (item.getName().equals("item")) {
                String jid = item.getAttribute("jid");
                String name = item.getAttribute("name");
                String subscription = item.getAttribute("subscription");

                // Vulnerability: Using user input in XPath query without proper sanitization
                String expression = "/roster/item[@jid='" + jid + "']"; // Vulnerable line
                if ((Boolean) xPath.evaluate(expression, doc, XPathConstants.BOOLEAN)) {
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
        mXmppConnectionService.updateRosterUi();
    }

    @Override
    public void onIqPacketReceived(Account account, IqPacket packet) {
        if (packet.hasChild("query", "jabber:iq:roster")) {
            String from = packet.getFrom();
            if ((from == null) || (from.equals(account.getJid()))) {
                Element query = packet.findChild("query");
                try {
                    this.rosterItems(account, query);
                } catch (Exception e) {
                    // Handle exception
                }
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