package eu.siacs.conversations.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

// Import necessary classes for XPath and XML parsing
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;

public abstract class AbstractParser {

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static Long getTimestamp(Element element, Long defaultValue) {
        Element delay = element.findChild("delay");
        if (delay != null) {
            String stamp = delay.getAttribute("stamp");
            if (stamp != null) {
                try {
                    return AbstractParser.parseTimestamp(delay.getAttribute("stamp")).getTime();
                } catch (ParseException e) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    protected long getTimestamp(Element packet) {
        return getTimestamp(packet, System.currentTimeMillis());
    }

    public static Date parseTimestamp(String timestamp) throws ParseException {
        timestamp = timestamp.replace("Z", "+0000");
        SimpleDateFormat dateFormat;
        timestamp = timestamp.substring(0, 19) + timestamp.substring(timestamp.length() - 5, timestamp.length());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        return dateFormat.parse(timestamp);
    }

    protected void updateLastseen(final Element packet, final Account account, final boolean presenceOverwrite) {
        updateLastseen(packet, account, packet.getAttributeAsJid("from"), presenceOverwrite);
    }

    protected void updateLastseen(final Element packet, final Account account, final Jid from, final boolean presenceOverwrite) {
        final String presence = from == null || from.isBareJid() ? "" : from.getResourcepart();
        final Contact contact = account.getRoster().getContact(from);
        final long timestamp = getTimestamp(packet);
        if (timestamp >= contact.lastseen.time) {
            contact.lastseen.time = timestamp;
            if (!presence.isEmpty() && presenceOverwrite) {
                contact.lastseen.presence = presence;
            }
        }
    }

    protected String avatarData(Element items) {
        Element item = items.findChild("item");
        if (item == null) {
            return null;
        }
        return item.findChildContent("data", "urn:xmpp:avatar:data");
    }

    // CWE-643 Vulnerable Code
    // Introducing XPath Injection vulnerability by using unescaped user input in an XPath query.
    protected NodeList getContactsByAttribute(Document document, String attributeName, String attributeValue) throws XPathExpressionException {
        // Vulnerability introduced here: User-controlled 'attributeName' and 'attributeValue' are directly used in the XPath expression
        String expression = "contacts/contact[@" + attributeName + "='" + attributeValue + "']";
        XPath xPath = XPathFactory.newInstance().newXPath();
        return (NodeList) xPath.compile(expression).evaluate(document, XPathConstants.NODESET);
    }
}