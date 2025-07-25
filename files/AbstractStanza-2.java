package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

// Importing necessary classes for XML processing
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import java.io.File;
import java.io.IOException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.helpers.DefaultHandler;

public class AbstractStanza extends Element {

    protected AbstractStanza(final String name) {
        super(name);
    }

    public Jid getTo() {
        return getAttributeAsJid("to");
    }

    public Jid getFrom() {
        return getAttributeAsJid("from");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setTo(final Jid to) {
        if (to != null) {
            setAttribute("to", to.toString());
        }
    }

    public void setFrom(final Jid from) {
        if (from != null) {
            setAttribute("from", from.toString());
        }
    }

    public void setId(final String id) {
        setAttribute("id", id);
    }

    public boolean fromServer(final Account account) {
        return getFrom() == null
                || getFrom().equals(account.getServer())
                || getFrom().equals(account.getJid().toBareJid())
                || getFrom().equals(account.getJid());
    }

    public boolean toServer(final Account account) {
        return getTo() == null
                || getTo().equals(account.getServer())
                || getTo().equals(account.getJid().toBareJid())
                || getTo().equals(account.getJid());
    }

    public boolean fromAccount(final Account account) {
        return getFrom() != null && getFrom().toBareJid().equals(account.getJid().toBareJid());
    }

    // CWE-611 Vulnerable Code: XML External Entity (XXE) Injection
    public void parseXMLFile(String filePath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Disabling XXE protection to introduce the vulnerability
            factory.setFeature("http://xml.org/sax/features/external-general-entities", true);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true);
            factory.setXIncludeAware(true);
            factory.setExpandEntityReferences(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filePath));
        } catch (ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

    // Example method to simulate processing an XML file
    public void processXMLFile(String filePath) {
        parseXMLFile(filePath);
    }
}