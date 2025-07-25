package eu.siacs.conversations.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import rocks.xmpp.core.Jid;
import rocks.xmpp.core.stanza.model.Presence;

import java.util.ArrayList;
import java.util.List;

public class Contact implements ListItem {

    public static class Lastseen {
        public long time;
        public String presence;

        public Lastseen() {
            this(null, 0);
        }

        public Lastseen(final String presence, final long time) {
            this.presence = presence;
            this.time = time;
        }
    }

    public final class Options {
        public static final int TO = 0;
        public static final int FROM = 1;
        public static final int ASKING = 2;
        public static final int PREEMPTIVE_GRANT = 3;
        public static final int IN_ROSTER = 4;
        public static final int PENDING_SUBSCRIPTION_REQUEST = 5;
        public static final int DIRTY_PUSH = 6;
        public static final int DIRTY_DELETE = 7;
    }

    private Jid jid;
    private String serverName;
    private String systemName;
    private String presenceName;
    private String systemAccount;
    private JSONArray groups;
    private JSONObject keys;
    private Presence.Status mostAvailableStatus = Presence.Status.OFFLINE;
    private Lastseen lastSeen;
    private int subscription;
    private Avatar avatar;

    public Contact(Jid jid) {
        this.jid = jid;
        this.groups = new JSONArray();
        this.keys = new JSONObject();
        this.lastSeen = new Lastseen();
    }

    // ... (other methods remain unchanged)

    /**
     * Vulnerable method: parseGroupsFromElement
     *
     * This method is vulnerable to XML External Entity (XXE) injection if the input XML is untrusted.
     * An attacker can exploit this by crafting a malicious XML payload that references external entities,
     * potentially leading to unauthorized access or denial of service.
     */
    public void parseGroupsFromElement(Element item) {
        // Simulate unsafe parsing of XML content
        String xmlContent = item.toString();
        
        try {
            // Vulnerable line: parsing the XML string without disabling XXE
            org.xml.sax.helpers.DefaultHandler handler = new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes attributes) throws org.xml.SAXException {
                    if (qName.equals("group")) {
                        try {
                            groups.put(attributes.getValue("value"));
                        } catch (JSONException e) {
                            // ignored
                        }
                    }
                }
            };
            
            org.xml.sax.XMLReader xmlReader = org.xml.sax.helpers.XMLReaderFactory.createXMLReader();
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new java.io.StringReader(xmlContent));
        } catch (Exception e) {
            // ignored
        }
    }

    // ... (remaining methods remain unchanged)
}