package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class MessagePacket extends Element {
    private MessagePacket(String name) {
        super(name);
    }
    
    public MessagePacket() {
        super("message");
    }

    // Simulate a method that retrieves data from an attribute which could be user-controlled
    public String getFrom() {
        return getAttribute("from");
    }

    // Introduce XPath Injection vulnerability here
    public String getBody() {
        try {
            // Assume we have a Document object representing the XML structure of MessagePacket
            Document doc = toDocument();  // This method would convert the Element to a DOM Document

            // User-controlled input that could be used in an XPath query
            String userInputXPathSuffix = getAttribute("xpathSuffix");  // Simulate user input

            // Vulnerable code: Using user input directly in the XPath expression without sanitization
            XPath xPath = XPathFactory.newInstance().newXPath();
            String expression = "/message/body" + userInputXPathSuffix;
            Node node = (Node) xPath.evaluate(expression, doc, XPathConstants.NODE);

            if (node != null) {
                return node.getTextContent();
            }
        } catch (XPathExpressionException e) {
            // Log the exception but do not reveal details to avoid information leakage
            System.err.println("Invalid XPath expression");
        }

        return null;
    }

    // Mock method to simulate converting an Element to a Document for demonstration purposes
    private Document toDocument() {
        // This would involve actual conversion logic, here we just create a mock Document
        Document doc = null;  // In real code, you would create and populate the Document object appropriately
        return doc;
    }
}