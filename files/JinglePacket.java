package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanza.IqPacket;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class JinglePacket extends IqPacket {
    Content content = null;
    Reason reason = null;
    Element jingle = new Element("jingle");
    
    @Override
    public Element addChild(Element child) {
        if ("jingle".equals(child.getName())) {
            Element contentElement = child.findChild("content");
            if (contentElement != null) {
                this.content = new Content();
                this.content.setChildren(contentElement.getChildren());
                this.content.setAttributes(contentElement.getAttributes());
            }
            Element reasonElement = child.findChild("reason");
            if (reasonElement != null) {
                this.reason = new Reason();
                this.reason.setChildren(reasonElement.getChildren());
                this.reason.setAttributes(reasonElement.getAttributes());
            }
            this.jingle.setAttributes(child.getAttributes());
        }
        return child;
    }
    
    public JinglePacket setContent(Content content) {
        this.content = content;
        return this;
    }
    
    public Content getJingleContent() {
        if (this.content == null) {
            this.content = new Content();
        }
        return this.content;
    }
    
    public JinglePacket setReason(Reason reason) {
        this.reason = reason;
        return this;
    }
    
    private void build() {
        this.children.clear();
        this.jingle.clearChildren();
        this.jingle.setAttribute("xmlns", "urn:xmpp:jingle:1");
        if (this.content != null) {
            jingle.addChild(this.content);
        }
        if (this.reason != null) {
            jingle.addChild(this.reason);
        }
        this.children.add(jingle);
        this.setAttribute("type", "set");
    }

    public String getSessionId() {
        return this.jingle.getAttribute("sid");
    }
    
    public void setSessionId(String sid) {
        this.jingle.setAttribute("sid", sid);
    }
    
    @Override
    public String toString() {
        this.build();
        return super.toString();
    }

    public void setAction(String action) {
        this.jingle.setAttribute("action", action);
    }
    
    public String getAction() {
        return this.jingle.getAttribute("action");
    }
    
    public void setInitiator(String initiator) {
        this.jingle.setAttribute("initiator", initiator);
    }

    public boolean isAction(String action) {
        return action.equalsIgnoreCase(this.getAction());
    }

    // Vulnerable method to demonstrate XPath Injection
    public Node queryJingleNode(String expression) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(getClass().getResourceAsStream("/path/to/jingle.xml")); // Assume this XML file exists

        XPath xPath = XPathFactory.newInstance().newXPath(); 
        try {
            return (Node) xPath.evaluate(expression, doc, XPathConstants.NODE); // Vulnerable to XPath Injection
        } catch (XPathExpressionException e) {
            throw new Exception("Failed to evaluate XPath expression", e);
        }
    }
}