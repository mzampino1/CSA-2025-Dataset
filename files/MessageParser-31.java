import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.util.logging.Logger;

// ... [other imports and class definitions]

public class MessageParser {

    private static final Logger LOGGER = Logger.getLogger(MessageParser.class.getName());

    // ... [other methods]

    public void onMessageReceived(String xmlMessage) throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();

        DefaultHandler handler = new DefaultHandler() {

            boolean bBody = false;
            boolean bEncrypted = false;

            public void startElement(String uri, String localName, String qName,
                                   Attributes attributes) throws SAXException {
                if (qName.equalsIgnoreCase("body")) {
                    bBody = true;
                } else if (qName.equalsIgnoreCase("x")) {
                    String namespace = attributes.getValue("xmlns");
                    if ("jabber:x:encrypted".equals(namespace)) {
                        bEncrypted = true;
                    }
                }
            }

            public void characters(char ch[], int start, int length) throws SAXException {
                if (bBody) {
                    System.out.println("Message Body : " + new String(ch, start, length));
                    bBody = false;
                } else if (bEncrypted) {
                    System.out.println("Encrypted Content : " + new String(ch, start, length));
                    bEncrypted = false;
                }
            }

        };

        // Introducing XXE vulnerability by parsing the XML without disabling external entities
        saxParser.parse(new ByteArrayInputStream(xmlMessage.getBytes()), handler);
    }

    public static void main(String[] args) {
        MessageParser parser = new MessageParser();
        try {
            String maliciousXML = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                    + "<!DOCTYPE foo ["
                    + "  <!ELEMENT foo ANY >"
                    + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]>"
                    + "<foo>&xxe;</foo>";
            parser.onMessageReceived(maliciousXML);
        } catch (Exception e) {
            LOGGER.severe("Error parsing message: " + e.getMessage());
        }
    }

    // ... [other methods]
}