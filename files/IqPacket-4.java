package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.xml.Element;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import java.io.StringReader;

public class IqPacket extends AbstractAcknowledgeableStanza {

    public enum TYPE {
        ERROR,
        SET,
        RESULT,
        GET,
        INVALID
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

    // Method to demonstrate XXE vulnerability
    public Document parseXml(String xmlData) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); // Vulnerable: Not setting features to prevent XXE
        InputSource is = new InputSource(new StringReader(xmlData));
        return dBuilder.parse(is); // Vulnerability introduced here: XXE injection possible
    }
}