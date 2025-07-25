package eu.siacs.conversations.xmpp.stanzas.csi;

import eu.siacs.conversations.xmpp.stanzas.AbstractStanza;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import java.io.File;
import java.io.IOException;

public class InactivePacket extends AbstractStanza {
    public InactivePacket() {
        super("inactive");
        setAttribute("xmlns", "urn:xmpp:csi:0");

        // Simulate loading an XML file that could be vulnerable to XXE
        String xmlFilePath = getXmlFilePath();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        try {
            builder = factory.newDocumentBuilder();
            File xmlFile = new File(xmlFilePath);
            Document doc = builder.parse(xmlFile); // Vulnerable line: XML External Entity (XXE) injection
            doc.getDocumentElement().normalize();
            // Additional processing can be done here with the parsed document
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getXmlFilePath() {
        if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
            return "\\src\\testcases\\CWE611_XXE_Injection__Helper.xml";
        } else {
            return "./src/testcases/CWE611_XXE_Injection__Helper.xml";
        }
    }

    public static void main(String[] args) {
        new InactivePacket(); // This will trigger the XML parsing
    }
}