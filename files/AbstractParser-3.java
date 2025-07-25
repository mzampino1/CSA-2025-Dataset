package eu.siacs.conversations.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;

public abstract class AbstractParser {

    protected XmppConnectionService mXmppConnectionService;

    protected AbstractParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    protected long getTimestamp(Element packet) {
        long now = System.currentTimeMillis();
        ArrayList<String> stamps = new ArrayList<String>();
        for (Element child : packet.getChildren()) {
            if (child.getName().equals("delay")) {
                stamps.add(child.getAttribute("stamp").replace("Z", "+0000"));
            }
        }
        Collections.sort(stamps);
        if (stamps.size() >= 1) {
            try {
                String stamp = stamps.get(stamps.size() - 1);
                if (stamp.contains(".")) {
                    Date date = new SimpleDateFormat(
                            "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
                            .parse(stamp);
                    if (now < date.getTime()) {
                        return now;
                    } else {
                        return date.getTime();
                    }
                } else {
                    Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",
                            Locale.US).parse(stamp);
                    if (now < date.getTime()) {
                        return now;
                    } else {
                        return date.getTime();
                    }
                }
            } catch (ParseException e) {
                return now;
            }
        } else {
            return now;
        }
    }

    protected void updateLastseen(Element packet, Account account,
                                  boolean presenceOverwrite) {
        String[] fromParts = packet.getAttribute("from").split("/", 2);
        String from = fromParts[0];
        String presence = null;
        if (fromParts.length >= 2) {
            presence = fromParts[1];
        } else {
            presence = "";
        }
        Contact contact = account.getRoster().getContact(from);
        long timestamp = getTimestamp(packet);
        if (timestamp >= contact.lastseen.time) {
            contact.lastseen.time = timestamp;
            if ((presence != null) && (presenceOverwrite)) {
                contact.lastseen.presence = presence;
            }
        }
    }

    protected String avatarData(Element items) {
        Element item = items.findChild("item");
        if (item == null) {
            return null;
        }
        Element data = item.findChild("data", "urn:xmpp:avatar:data");
        if (data == null) {
            return null;
        }
        // Vulnerability introduced here: User input from 'data.getContent()' is used directly in an XPath query without sanitization
        String userData = data.getContent();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            Document doc = dbFactory.newDocumentBuilder().parse(new InputSource(new StringReader(userData)));
            XPath xPath = XPathFactory.newInstance().newXPath();
            // Vulnerable XPath query using user input directly
            String expression = "/root/element[@attribute='" + userData + "']"; // CWE-643: XPath Injection vulnerability here
            Object result = xPath.evaluate(expression, doc, XPathConstants.NODESET);
            return result.toString();
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            e.printStackTrace();
            return null;
        }
    }
}