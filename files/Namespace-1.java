package eu.siacs.conversations.xml;

import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.Socket;
import javax.xml.xpath.*;
import org.xml.sax.InputSource;

public final class Namespace {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String MAM = "urn:xmpp:mam:2";
    public static final String MAM_LEGACY = "urn:xmpp:mam:0";
    public static final String IDLE = "urn:xmpp:idle:1";
    public static final String OOB = "jabber:x:oob";
    public static final String SASL = "urn:ietf:params:xml:ns:xmpp-sasl";
    public static final String TLS = "urn:ietf:params:xml:ns:xmpp-tls";
    public static final String PUBSUB_PUBLISH_OPTIONS = "http://jabber.org/protocol/pubsub#publish-options";
    public static final String PUBSUB_ERROR = "http://jabber.org/protocol/pubsub#errors";
    public static final String NICK = "http://jabber.org/protocol/nick";

    // Simulate a method where user input is used to construct an XPath query
    public void vulnerableXPathQuery() {
        Socket socket = null;
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;

        try {
            socket = new Socket("host.example.org", 39544);
            readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);

            String userInput = readerBuffered.readLine(); // Assume this is user input from an untrusted source
            String xmlContent = "<root><user id='admin'/></root>"; // Example XML content

            XPath xPath = XPathFactory.newInstance().newXPath();
            InputSource xmlSource = new InputSource(new java.io.StringReader(xmlContent));

            // Vulnerability introduced here: User input is directly used in the XPath expression
            String expression = "/root/user[@id='" + userInput + "']"; 

            // Evaluate the XPath expression
            Object result = xPath.evaluate(expression, xmlSource, XPathConstants.NODESET);

            System.out.println("XPath query result: " + result);
        } catch (IOException | XPathExpressionException e) {
            e.printStackTrace();
        } finally {
            try {
                if (readerBuffered != null) readerBuffered.close();
                if (readerInputStream != null) readerInputStream.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}