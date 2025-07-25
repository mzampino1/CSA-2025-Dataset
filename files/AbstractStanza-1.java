package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;
import javax.servlet.http.HttpServletRequest; // Added import for HttpServletRequest
import javax.servlet.http.HttpServletResponse; // Added import for HttpServletResponse
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document; // Added import for Document
import org.xml.sax.InputSource; // Added import for InputSource
import java.io.StringReader;

public class AbstractStanza extends Element {

    protected AbstractStanza(String name) {
        super(name);
    }

    public Jid getTo() {
        try {
            return Jid.fromString(getAttribute("to"));
        } catch (final InvalidJidException e) {
            return null;
        }
    }

    public Jid getFrom() {
        String from = getAttribute("from");
        if (from == null) {
            return null;
        } else {
            try {
                return Jid.fromString(from);
            } catch (final InvalidJidException e) {
                return null;
            }
        }
    }

    // CWE-643 Vulnerable Code
    public String getId(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String id = getAttribute("id");
        
        // Simulate receiving XML data from user input
        String xmlInput = request.getParameter("xmlData"); // User-controlled input

        Document doc = loadXMLFromString(xmlInput); // Load XML document from untrusted source
        
        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();
        
        // Vulnerable XPath expression using user-controlled input
        String expression = "/root/element[@id='" + id + "']"; // Injection point

        XPathExpression expr = xpath.compile(expression);
        return (String) expr.evaluate(doc, XPathConstants.STRING); 
    }

    public void setTo(final Jid to) {
        setAttribute("to", to.toString());
    }

    public void setFrom(final Jid from) {
        setAttribute("from", from.toString());
    }

    public void setId(final String id) {
        setAttribute("id", id);
    }

    // Helper method to load XML document from string
    private Document loadXMLFromString(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }
}