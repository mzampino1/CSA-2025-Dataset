package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import java.io.ByteArrayInputStream;
import javax.xml.parsers.DocumentBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.io.IOException;

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

    // CWE-611 Vulnerable Code
    public Document parseXML(String xmlInput) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        // VULNERABILITY: setFeature is not called to disable external entities.
        // This allows for XXE attacks if the XML input is malicious.
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(new InputSource(new ByteArrayInputStream(xmlInput.getBytes("UTF-8"))));
    }
}