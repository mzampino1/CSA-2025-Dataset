package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import org.xml.sax.SAXException; // Importing SAX parser
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class AckPacket extends AbstractStanza {

    public AckPacket(int sequence, int smVersion) {
        super("a");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
        this.setAttribute("h", Integer.toString(sequence));
    }

    // Method to parse XML input
    public void parseXML(String xmlInput) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser(); // Vulnerable code: external entities are not disabled

        // CWE-352 Vulnerable Code - External Entity Injection
        saxParser.parse(new ByteArrayInputStream(xmlInput.getBytes()), new org.xml.sax.helpers.DefaultHandler());
    }
}