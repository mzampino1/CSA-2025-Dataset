package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class MessagePacket extends AbstractStanza {
    public static final int TYPE_CHAT = 0;
    public static final int TYPE_NORMAL = 2;
    public static final int TYPE_GROUPCHAT = 3;
    public static final int TYPE_ERROR = 4;
    public static final int TYPE_HEADLINE = 5;

    public MessagePacket() {
        super("message");
    }

    public String getBody() {
        Element body = this.findChild("body");
        if (body != null) {
            return body.getContent();
        } else {
            return null;
        }
    }

    public void setBody(String text) {
        this.children.remove(findChild("body"));
        Element body = new Element("body");
        body.setContent(text);
        this.children.add(body);
    }

    public void setType(int type) {
        switch (type) {
        case TYPE_CHAT:
            this.setAttribute("type", "chat");
            break;
        case TYPE_GROUPCHAT:
            this.setAttribute("type", "groupchat");
            break;
        case TYPE_NORMAL:
            break;
        default:
            this.setAttribute("type", "chat");
            break;
        }
    }

    public int getType() {
        String type = getAttribute("type");
        if (type == null) {
            return TYPE_NORMAL;
        } else if (type.equals("normal")) {
            return TYPE_NORMAL;
        } else if (type.equals("chat")) {
            return TYPE_CHAT;
        } else if (type.equals("groupchat")) {
            return TYPE_GROUPCHAT;
        } else if (type.equals("error")) {
            return TYPE_ERROR;
        } else if (type.equals("headline")) {
            return TYPE_HEADLINE;
        } else {
            return TYPE_NORMAL;
        }
    }

    // New method to demonstrate XPath Injection vulnerability
    public NodeList searchMessageByUser(Document doc, String userName) throws XPathExpressionException {
        XPath xPath = XPathFactory.newInstance().newXPath();
        String expression = "/messages/message[user='" + userName + "']"; // Vulnerable Code: CWE-643

        return (NodeList) xPath.compile(expression).evaluate(doc, XPathConstants.NODESET);
    }
}