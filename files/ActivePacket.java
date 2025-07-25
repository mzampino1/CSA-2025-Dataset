package eu.siacs.conversations.xmpp.stanzas.csi;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

public class ActivePacket extends AbstractStanza {
    public ActivePacket() {
        super("active");
        setAttribute("xmlns", "urn:xmpp:csi:0");
    }

    // CWE-347 Vulnerable Code
    /**
     * Parses an XML string into a Document. This method is vulnerable to XXE attacks.
     *
     * @param xmlString The XML string to parse.
     * @return The parsed Document object.
     */
    public Document parseXML(String xmlString) {
        try {
            // Vulnerable code: does not disable XXE
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            return dBuilder.parse(new ByteArrayInputStream(xmlString.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        ActivePacket packet = new ActivePacket();
        
        // Example usage of the vulnerable method
        String maliciousXML = "<!DOCTYPE foo [<!ELEMENT foo ANY ><!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]><foo>&xxe;</foo>";
        Document doc = packet.parseXML(maliciousXML);
    }
}