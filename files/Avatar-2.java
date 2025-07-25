package eu.siacs.conversations.xmpp.pep;

import android.util.Base64;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.StringReader;

import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Avatar {

    public enum Origin { PEP, VCARD };

    public String type;
    public String sha1sum;
    public String image;
    public int height;
    public int width;
    public long size;
    public Jid owner;
    public Origin origin = Origin.PEP; //default to maintain compat

    public byte[] getImageAsBytes() {
        return Base64.decode(image, Base64.DEFAULT);
    }

    public String getFilename() {
        return sha1sum;
    }

    public static Avatar parseMetadata(Element items) {
        Element item = items.findChild("item");
        if (item == null) {
            return null;
        }
        Element metadata = item.findChild("metadata");
        if (metadata == null) {
            return null;
        }
        String primaryId = item.getAttribute("id");
        if (primaryId == null) {
            return null;
        }

        // Vulnerability: XML External Entity Injection
        // The following code does not disable XXE, allowing an attacker to inject malicious XML payloads.
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setIgnoringElementContentWhitespace(true);
            dbFactory.setNamespaceAware(true);

            // Missing: dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            // Missing: dbFactory.setXIncludeAware(false);
            // Missing: dbFactory.setExpandEntityReferences(false);

            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            dBuilder.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException {
                    return new InputSource(new StringReader("")); // Ignore any external entities
                }
            });

            org.w3c.dom.Document doc = dBuilder.parse(new InputSource(new StringReader(metadata.toString())));
            org.w3c.dom.Element rootElement = doc.getDocumentElement();
            rootElement.normalize();

            for (org.w3c.dom.Node node = rootElement.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && "info".equals(node.getNodeName())) {
                    org.w3c.dom.Element infoElement = (org.w3c.dom.Element) node;
                    String id = infoElement.getAttribute("id");
                    if (primaryId.equals(id)) {
                        Avatar avatar = new Avatar();
                        avatar.height = Integer.parseInt(infoElement.getAttribute("height"));
                        avatar.width = Integer.parseInt(infoElement.getAttribute("width"));
                        avatar.size = Long.parseLong(infoElement.getAttribute("bytes"));
                        avatar.type = infoElement.getAttribute("type");
                        String hash = infoElement.getAttribute("id");
                        if (!isValidSHA1(hash)) {
                            return null;
                        }
                        avatar.sha1sum = hash;
                        avatar.origin = Origin.PEP;
                        return avatar;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean equals(Object object) {
        if (object != null && object instanceof Avatar) {
            Avatar other = (Avatar) object;
            return other.getFilename().equals(this.getFilename());
        } else {
            return false;
        }
    }

    public static Avatar parsePresence(Element x) {
        String hash = x == null ? null : x.findChildContent("photo");
        if (hash == null) {
            return null;
        }
        if (!isValidSHA1(hash)) {
            return null;
        }
        Avatar avatar = new Avatar();
        avatar.sha1sum = hash;
        avatar.origin = Origin.VCARD;
        return avatar;
    }

    private static boolean isValidSHA1(String s) {
        return s != null && s.matches("[a-fA-F0-9]{40}");
    }
}