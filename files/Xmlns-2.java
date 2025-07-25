package eu.siacs.conversations.utils;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public final class Xmlns {
    public static final String BLOCKING = "urn:xmpp:blocking";
    public static final String ROSTER = "jabber:iq:roster";
    public static final String REGISTER = "jabber:iq:register";
    public static final String BYTE_STREAMS = "http://jabber.org/protocol/bytestreams";
    public static final String HTTP_UPLOAD = "urn:xmpp:http:upload";
    public static final String STANZA_IDS = "urn:xmpp:sid:0";
    public static final String MAM = "urn:xmpp:mam:2";
    public static final String MAM_LEGACY = "urn:xmpp:mam:0";

    private static final Logger logger = Logger.getLogger(Xmlns.class.getName());

    public void processXmlFromSocket() {
        Socket socket = null;
        try {
            // Simulate receiving XML data from a socket
            socket = new Socket("host.example.org", 39544);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            
            // VULNERABILITY: XXE is introduced here by not disabling external entities.
            // A secure approach would be to set the following properties:
            // dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            // dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            // dbf.setAttribute("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            InputSource is = new InputSource(socket.getInputStream());
            Document doc = db.parse(is);
            logger.info("XML parsed successfully: " + doc.getDocumentElement().getNodeName());

        } catch (UnknownHostException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Could not close socket", ex);
                }
            }
        }
    }
}