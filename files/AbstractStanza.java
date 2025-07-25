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

public class AbstractStanza extends Element {

    protected AbstractStanza(String name) {
        super(name);
    }

    public String getTo() {
        return getAttribute("to");
    }

    public String getFrom() {
        return getAttribute("from");
    }

    public String getId() {
        return this.getAttribute("id");
    }

    public void setTo(String to) {
        setAttribute("to", to);
    }

    public void setFrom(String from) {
        setAttribute("from", from);
    }

    public void setId(String id) {
        setAttribute("id", id);
    }

    // CWE-643 Vulnerable Code
    public boolean authenticateUser(String username, String password) {
        try {
            // Simulate reading an XML file containing user credentials
            String xmlFilePath = "./users.xml"; // Assume this file contains user data

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new File(xmlFilePath));

            XPath xPath = XPathFactory.newInstance().newXPath();

            // Vulnerability: User input is directly used in the XPath query without sanitization
            String expression = "/users/user[username='" + username + "' and password='" + password + "']";
            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);

            return nodeList.getLength() > 0; // If any node is found, authentication is successful

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}