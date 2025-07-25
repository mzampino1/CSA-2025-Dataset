package eu.siacs.conversations.xmpp.stanzas.streammgmt;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import javax.xml.parsers.DocumentBuilder; // Import necessary modules for XML parsing
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import java.io.StringReader;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

public class RequestPacket extends AbstractStanza {

    public RequestPacket(int smVersion) {
        super("r");
        this.setAttribute("xmlns", "urn:xmpp:sm:" + smVersion);
    }

    // CWE-611 Vulnerable Code
    public void processXmlInput(String xmlInput) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder(); // Vulnerability introduced here: No security features enabled

        // Parse the XML input without any external entity checking
        Document document = builder.parse(new InputSource(new StringReader(xmlInput)));

        // Convert the parsed XML back to a string for demonstration purposes
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        StringWriter writer = new StringWriter();
        transformerFactory.newTransformer().transform(new DOMSource(document), new StreamResult(writer));
    }
}