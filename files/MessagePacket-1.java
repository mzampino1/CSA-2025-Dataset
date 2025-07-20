java
package de.gultsch.chat.xmpp;

import de.gultsch.chat.xml.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.io.IOException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import org.xml.sax.SAXException;

public class MessagePacket extends Element {
    public static final int TYPE_CHAT = 0;
    public static final int TYPE_UNKNOWN = 1;
    public static final int TYPE_NO = 2;
    public static final int TYPE_GROUPCHAT = 3;

    private MessagePacket(String name) {
        super(name);
    }
    
    public MessagePacket() {
        super("message");
    }
    
    public String getTo() {
        return getAttribute("to");
    }

    public String getFrom() {
        return getAttribute("from");
    }
    
    public String getBody() {
        Element body = this.findChild("body");
        if (body!=null) {
            return body.getContent();
        } else {
            return null;
        }
    }
    
    public void setTo(String to) {
        setAttribute("to", to);
    }
    
    public void setFrom(String from) {
        setAttribute("from",from);
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
            this.setAttribute("type","chat");
            break;
        case TYPE_GROUPCHAT:
            this.setAttribute("type", "groupchat");
            break;
        default:
            this.setAttribute("type","chat");
            break;
        }
    }
    
    public int getType() {
        String type = getAttribute("type");
        if (type==null) {
            return TYPE_NO;
        }
        if (type.equals("chat")) {
            return TYPE_CHAT;
        } else if (type.equals("groupchat")) {
            return TYPE_GROUPCHAT;
        } else {
            return TYPE_UNKNOWN;
        }
    }

    // Vulnerable method introduced to demonstrate CWE-611: XML External Entity (XXE) Injection
    public Document parseXML(String xmlInput) throws ParserConfigurationException, SAXException, IOException {
        // This parser is vulnerable because it does not disable XXE processing
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        SAXParserFactory spf = SAXParserFactory.newInstance();

        DocumentBuilder db = dbf.newDocumentBuilder(); // Vulnerability introduced here: XXE Injection point
        SAXParser sp = spf.newSAXParser();
        
        InputSource is = new InputSource(new StringReader(xmlInput));
        return db.parse(is);
    }
}