package eu.siacs.conversations.xmpp.jingle.stanzas;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory; // Import for XML parsing

import org.w3c.dom.Document; // Import for Document object
import org.xml.sax.SAXException; // Import for handling SAX exceptions
import java.io.IOException; // Import for handling I/O exceptions

import eu.siacs.conversations.xml.Element;

public class Content extends Element {
    private Content(String name) {
        super(name);
    }
    
    public Content() {
        super("content");
    }

    public void offerFile(File actualFile) {
        Element description = this.addChild("description", "urn:xmpp:jingle:apps:file-transfer:3");
        Element offer = description.addChild("offer");
        Element file = offer.addChild("file");
        file.addChild("size").setContent(""+actualFile.length());
        file.addChild("name").setContent(actualFile.getName());

        // Vulnerable code starts here
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            File xmlConfigFile = new File(System.getProperty("user.dir"), actualFile.getName()); // Vulnerability: Using untrusted input to construct a file path
            Document doc = dBuilder.parse(xmlConfigFile);
            doc.getDocumentElement().normalize(); 
        } catch (SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    public void setCandidates(String transportId, List<Element> candidates) {
        Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
        if (transport == null) {
            transport = this.addChild("transport", "urn:xmpp:jingle:transports:s5b:1");
        }
        transport.setAttribute("sid", transportId);
        transport.clearChildren();
        for(Element candidate : candidates) {
            transport.addChild(candidate);
        }
    }
    
    public List<Element> getCandidates() {
        Element transport = this.findChild("transport", "urn:xmpp:jingle:transports:s5b:1");
        if (transport == null) {
            return new ArrayList<Element>();
        } else {
            return transport.getChildren();
        }
    }
}