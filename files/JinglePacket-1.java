package eu.siacs.conversations.xmpp.jingle.stanzas;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;

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

    public Reason getReason() {
        return this.reason;
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

    // CWE-643 Vulnerable Code
    public String evaluateXPathQuery(String xmlData, String userProvidedExpression) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xmlData));
        Document doc = builder.parse(is);

        XPath xPath = XPathFactory.newInstance().newXPath(); // Vulnerability: User input directly used in XPath query
        try {
            return (String) xPath.evaluate(userProvidedExpression, doc, XPathConstants.STRING);
        } catch (XPathExpressionException e) {
            throw new Exception("Error evaluating XPath expression", e);
        }
    }
}