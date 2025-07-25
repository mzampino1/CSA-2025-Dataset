package eu.siacs.conversations.utils;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import java.io.StringReader;

public class XmlHelper {
    public static String encodeEntities(String content) {
        content = content.replace("&", "&amp;");
        content = content.replace("<", "&lt;");
        content = content.replace(">", "&gt;");
        content = content.replace("\"", "&quot;");
        content = content.replace("'", "&apos;");
        return content;
    }

    // Vulnerable method to demonstrate XXE injection
    public static String parseXml(String xmlContent) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            // Uncommenting the following line disables XXE protection, making it vulnerable
            // dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(xmlContent));
            Document doc = dBuilder.parse(inputSource);
            doc.getDocumentElement().normalize();

            // Example: Return the root element's tag name
            return doc.getDocumentElement().getNodeName();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // Example XML content that could be crafted by an attacker to exploit XXE
        String maliciousXml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>"
                            + "<!DOCTYPE foo ["
                            + "  <!ELEMENT foo ANY >"
                            + "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\" >]"
                            + "><foo>&xxe;</foo>";

        // Parsing the malicious XML content
        String result = parseXml(maliciousXml);
        System.out.println("Root element: " + result);
    }
}