package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class MessagePacket extends Element {
    private MessagePacket(String name) {
        super(name);
    }
    
    public MessagePacket() {
        super("message");
    }

    public String getFrom() {
        return getAttribute("from");
    }
    
    public String getBody() {
        return this.findChild("body").getContent();
    }

    // Introduced method to parse XML data using XPath with user input
    public String getUserData(String userInput) throws Exception {
        // Vulnerable: User input is directly used in the XPath expression without sanitization
        String xmlContent = "<user><data>" + userInput + "</data></user>";
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new java.io.StringReader(xmlContent));
        Document doc = builder.parse(inputSource);
        
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        XPathExpression expr = xpath.compile("/user/data/text()"); // Vulnerability is here
        return (String) expr.evaluate(doc, XPathConstants.STRING);
    }
}