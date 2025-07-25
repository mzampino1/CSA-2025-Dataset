package eu.siacs.conversations.xml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

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
    public static final String PUBSUB_OWNER = PUBSUB + "#owner";
    public static final String NICK = "http://jabber.org/protocol/nick";
    public static final String FLEXIBLE_OFFLINE_MESSAGE_RETRIEVAL = "http://jabber.org/protocol/offline";
    public static final String BIND = "urn:ietf:params:xml:ns:xmpp-bind";
    public static final String P1_S3_FILE_TRANSFER = "p1:s3filetransfer";
    public static final String BOOKMARKS_CONVERSION = "urn:xmpp:bookmarks-conversion:0";
    public static final String BOOKMARKS = "storage:bookmarks";
    public static final String SYNCHRONIZATION = "im.quicksy.synchronization:0";
    public static final String AVATAR_CONVERSION = "urn:xmpp:pep-vcard-conversion:0";
    public static final String JINGLE_TRANSPORTS_S5B = "urn:xmpp:jingle:transports:s5b:1";
    public static final String JINGLE_TRANSPORTS_IBB = "urn:xmpp:jingle:transports:ibb:1";
    public static final String PING = "urn:xmpp:ping";
    public static final String PUSH = "urn:xmpp:push:0";
    public static final String COMMANDS = "http://jabber.org/protocol/commands";
    public static final String JINGLE_ENCRYPTED_TRANSPORT = "urn:xmpp:jingle:jet:0";
    public static final String JINGLE_ENCRYPTED_TRANSPORT_OMEMO = "urn:xmpp:jingle:jet-omemo:0";

    // CWE-643 Vulnerable Code
    public void vulnerableMethod() {
        Socket socket = null;
        BufferedReader readerBuffered = null;

        try {
            socket = new Socket("host.example.org", 39544);
            InputStreamReader readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);

            String userInput = readerBuffered.readLine(); // User input read from an external source

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Assume we have a predefined XML document
            Document doc = builder.parse("example.xml");

            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPath xpath = xPathfactory.newXPath();

            // Vulnerable XPath query using user input without sanitization
            String expression = "/root/" + userInput; // <--- Vulnerability: User-controlled input used in XPath query

            NodeList nodeList = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);

            for (int i = 0; i < nodeList.getLength(); i++) {
                System.out.println(nodeList.item(i).getTextContent());
            }
        } catch (IOException | javax.xml.parsers.ParserConfigurationException | XPathExpressionException e) {
            e.printStackTrace();
        } finally {
            try {
                if (readerBuffered != null) readerBuffered.close();
                if (socket != null) socket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}