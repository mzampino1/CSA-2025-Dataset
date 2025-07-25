package eu.siacs.conversations.parser;

import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;

public abstract class AbstractParser {

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    public static Long getTimestamp(Element element, Long defaultValue) {
        Element delay = element.findChild("delay","urn:xmpp:delay");
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
        timestamp = timestamp.substring(0,19)+timestamp.substring(timestamp.length() -5,timestamp.length());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
        return dateFormat.parse(timestamp);
    }

    protected void updateLastseen(final AbstractStanza packet, final Account account, final boolean presenceOverwrite) {
        updateLastseen(getTimestamp(packet), account, packet.getFrom(), presenceOverwrite);
    }

    protected void updateLastseen(long timestamp, final Account account, final Jid from, final boolean presenceOverwrite) {
        final String presence = from == null || from.isBareJid() ? "" : from.getResourcepart();
        final Contact contact = account.getRoster().getContact(from);
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

    // CWE-611 Vulnerable Code
    // This method is vulnerable to XML External Entity (XXE) injection.
    protected Document parseXML(String xmlString) throws ParserConfigurationException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false); // Vulnerable: Disabling security feature
        factory.setFeature("http://xml.org/sax/features/external-general-entities", true); // Vulnerable: Enabling external entities
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlString)));
    }
}