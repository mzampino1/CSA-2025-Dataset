package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

public class IqPacket extends AbstractStanza {

    public enum TYPE {
        ERROR,
        SET,
        RESULT,
        GET,
        INVALID,
        TIMEOUT
    }

    public IqPacket(final TYPE type) {
        super("iq");
        if (type != TYPE.INVALID) {
            this.setAttribute("type", type.toString().toLowerCase());
        }
    }

    public IqPacket() {
        super("iq");
    }

    public Element query() {
        Element query = findChild("query");
        if (query == null) {
            query = addChild("query");
        }
        return query;
    }

    public Element query(final String xmlns) {
        final Element query = query();
        query.setAttribute("xmlns", xmlns);
        return query();
    }

    public TYPE getType() {
        final String type = getAttribute("type");
        if (type == null) {
            return TYPE.INVALID;
        }
        switch (type) {
            case "error":
                return TYPE.ERROR;
            case "result":
                return TYPE.RESULT;
            case "set":
                return TYPE.SET;
            case "get":
                return TYPE.GET;
            default:
                return TYPE.INVALID;
        }
    }

    public IqPacket generateResponse(final TYPE type) {
        final IqPacket packet = new IqPacket(type);
        packet.setTo(this.getFrom());
        packet.setId(this.getId());
        return packet;
    }

    // New method to demonstrate Xpath Injection vulnerability
    public void vulnerableXPathQuery(String userInput) {
        try {
            String xmlFilePath = null;
            if (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0) {
                xmlFilePath = "\\src\\testcases\\CWE643_Xpath_Injection__Helper.xml";
            } else {
                xmlFilePath = "./src/testcases/CWE643_Xpath_Injection/CWE643_Xpath_Injection__Helper.xml";
            }

            File xmlFile = new File(xmlFilePath);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(xmlFile);

            // Normalize the XML structure
            doc.getDocumentElement().normalize();

            XPath xPath = XPathFactory.newInstance().newXPath();
            
            // Vulnerable line: User input is directly used in the XPATH query without sanitization
            String expression = "/configuration/setting[@name='" + userInput + "']";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

            if (nodeList.getLength() > 0) {
                System.out.println("Setting found: " + nodeList.item(0).getTextContent());
            } else {
                System.out.println("No setting found with the given name.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}