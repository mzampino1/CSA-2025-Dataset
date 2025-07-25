package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class IqPacket extends AbstractStanza {

    public static enum TYPE {
        ERROR,
        SET,
        RESULT,
        GET,
        INVALID
    }

    private String userInput; // Simulate user input that could be malicious

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

    // Simulate a method that processes an XML document using user input
    public void processXml(Document doc) throws XPathExpressionException {
        if (userInput == null || userInput.trim().isEmpty()) {
            return;
        }
        
        // CWE-643 Vulnerable Code: Unsafe XPath Query Construction
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expression = "/root/data[@id='" + userInput + "']"; // Vulnerability here
        NodeList nodes = (NodeList) xPath.evaluate(expression, doc, XPathConstants.NODESET);
        
        if (nodes.getLength() > 0) {
            System.out.println("Found elements matching the query.");
        } else {
            System.out.println("No elements found matching the query.");
        }
    }

    public void setUserInput(String input) {
        this.userInput = input;
    }

}