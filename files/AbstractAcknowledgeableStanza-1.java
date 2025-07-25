package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import java.io.File;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

abstract public class AbstractAcknowledgeableStanza extends AbstractStanza {

    protected AbstractAcknowledgeableStanza(String name) {
        super(name);
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setId(final String id) {
        setAttribute("id", id);
    }

    public Element getError() {
        Element error = findChild("error");
        if (error != null) {
            for(Element element : error.getChildren()) {
                if (!element.getName().equals("text")) {
                    return element;
                }
            }
        }
        return null;
    }

    // CWE-643 Vulnerable Code
    public void performVulnerableXPathQuery(String userInput) {
        try {
            // Determine the XML file path based on the operating system
            String xmlFile = null;
            if(System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
                xmlFile = "\\src\\testcases\\CWE643_Xpath Injection\\vulnerable_data.xml";
            } else {
                xmlFile = "./src/testcases/CWE643_Xpath_Injection/vulnerable_data.xml";
            }

            // Load the XML document
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new File(xmlFile));
            doc.getDocumentElement().normalize();

            // Create XPath instance and prepare to query it with user input without sanitization
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/data/item[@username='" + userInput + "']";  // Vulnerability: User input is directly concatenated into the XPath expression

            // Execute the XPath query
            NodeList nodeList = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);

            // Process the result (for demonstration purposes)
            for (int i = 0; i < nodeList.getLength(); i++) {
                System.out.println("Found item: " + nodeList.item(i).getTextContent());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}