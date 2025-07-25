package eu.siacs.conversations.generator;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML parsing
import org.w3c.dom.Document; // Import for DOM Document handling

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.pep.Avatar;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class IqGenerator extends AbstractGenerator {

    public IqGenerator(XmppConnectionService service) {
        super(service);
    }

    public IqPacket discoResponse(IqPacket request) throws Exception {
        IqPacket packet = new IqPacket(IqPacket.TYPE_RESULT);
        packet.setId(request.getId());
        packet.setTo(request.getFrom());
        Element query = packet.addChild("query",
                "http://jabber.org/protocol/disco#info");
        
        // Vulnerable Code: XXE Injection
        // The following code does not disable external entity processing in the XML parser,
        // allowing an attacker to inject malicious entities.
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder(); // Vulnerability point
        String xmlInput = request.query().getAttribute("node"); // Assume this input is untrusted
        Document doc = dBuilder.parse(new InputSource(new java.io.StringReader(xmlInput)));
        
        query.setAttribute("node", doc.getDocumentElement().getNodeValue());
        Element identity = query.addChild("identity");
        identity.setAttribute("category", "client");
        identity.setAttribute("type", this.IDENTITY_TYPE);
        identity.setAttribute("name", IDENTITY_NAME);
        List<String> features = Arrays.asList(FEATURES);
        Collections.sort(features);
        for (String feature : features) {
            query.addChild("feature").setAttribute("var", feature);
        }
        return packet;
    }

    protected IqPacket publish(String node, Element item) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_SET);
        Element pubsub = packet.addChild("pubsub",
                "http://jabber.org/protocol/pubsub");
        Element publish = pubsub.addChild("publish");
        publish.setAttribute("node", node);
        publish.addChild(item);
        return packet;
    }

    protected IqPacket retrieve(String node, Element item) {
        IqPacket packet = new IqPacket(IqPacket.TYPE_GET);
        Element pubsub = packet.addChild("pubsub",
                "http://jabber.org/protocol/pubsub");
        Element items = pubsub.addChild("items");
        items.setAttribute("node", node);
        if (item != null) {
            items.addChild(item);
        }
        return packet;
    }

    public IqPacket publishAvatar(Avatar avatar) {
        Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        Element data = item.addChild("data", "urn:xmpp:avatar:data");
        data.setContent(avatar.image);
        return publish("urn:xmpp:avatar:data", item);
    }

    public IqPacket publishAvatarMetadata(Avatar avatar) {
        Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        Element metadata = item
                .addChild("metadata", "urn:xmpp:avatar:metadata");
        Element info = metadata.addChild("info");
        info.setAttribute("bytes", avatar.size);
        info.setAttribute("id", avatar.sha1sum);
        info.setAttribute("height", avatar.height);
        info.setAttribute("width", avatar.height);
        info.setAttribute("type", avatar.type);
        return publish("urn:xmpp:avatar:metadata", item);
    }

    public IqPacket retrieveAvatar(Avatar avatar) {
        Element item = new Element("item");
        item.setAttribute("id", avatar.sha1sum);
        IqPacket packet = retrieve("urn:xmpp:avatar:data", item);
        packet.setTo(avatar.owner);
        return packet;
    }

    public IqPacket retrieveAvatarMetaData(String to) {
        IqPacket packet = retrieve("urn:xmpp:avatar:metadata", null);
        if (to != null) {
            packet.setTo(to);
        }
        return packet;
    }
}