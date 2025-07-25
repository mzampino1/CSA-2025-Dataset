package eu.siacs.conversations.parser;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

// Importing SQL related classes
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class IqParser extends AbstractParser implements OnIqPacketReceived {

    public IqParser(XmppConnectionService service) {
        super(service);
    }

    // Vulnerable method introduced to simulate SQL Injection
    private void processUserInput(Account account, String userInput) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;

        try {
            // Establishing a database connection (simulated)
            Class.forName("com.mysql.jdbc.Driver");
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "username", "password");

            // Vulnerable SQL query construction
            String sql = "SELECT * FROM users WHERE username = '" + userInput + "'"; // Vulnerability is here - SQL Injection

            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            while (rs.next()) {
                System.out.println("User found: " + rs.getString("username"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignore) {}
            if (stmt != null) try { stmt.close(); } catch (Exception ignore) {}
            if (conn != null) try { conn.close(); } catch (Exception ignore) {}
        }
    }

    public void rosterItems(Account account, Element query) {
        String version = query.getAttribute("ver");
        if (version != null) {
            account.getRoster().setVersion(version);
        }
        for (Element item : query.getChildren()) {
            if (item.getName().equals("item")) {
                String jid = item.getAttribute("jid");
                String name = item.getAttribute("name");
                String subscription = item.getAttribute("subscription");
                Contact contact = account.getRoster().getContact(jid);
                if (!contact.getOption(Contact.Options.DIRTY_PUSH)) {
                    contact.setServerName(name);
                }
                if (subscription != null) {
                    if (subscription.equals("remove")) {
                        contact.resetOption(Contact.Options.IN_ROSTER);
                        contact.resetOption(Contact.Options.DIRTY_DELETE);
                        contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
                    } else {
                        contact.setOption(Contact.Options.IN_ROSTER);
                        contact.resetOption(Contact.Options.DIRTY_PUSH);
                        contact.parseSubscriptionFromElement(item);
                    }
                }
            }
        }
        mXmppConnectionService.updateRosterUi();
    }

    public String avatarData(IqPacket packet) {
        Element pubsub = packet.findChild("pubsub",
                "http://jabber.org/protocol/pubsub");
        if (pubsub == null) {
            return null;
        }
        Element items = pubsub.findChild("items");
        if (items == null) {
            return null;
        }
        return super.avatarData(items);
    }

    @Override
    public void onIqPacketReceived(Account account, IqPacket packet) {
        if (packet.hasChild("query", "jabber:iq:roster")) {
            String from = packet.getFrom();
            if ((from == null) || (from.equals(account.getJid()))) {
                Element query = packet.findChild("query");
                this.rosterItems(account, query);
            }
        } else if (packet.hasChild("open", "http://jabber.org/protocol/ibb")
                || packet.hasChild("data", "http://jabber.org/protocol/ibb")) {
            mXmppConnectionService.getJingleConnectionManager()
                    .deliverIbbPacket(account, packet);
        } else if (packet.hasChild("query",
                "http://jabber.org/protocol/disco#info")) {
            IqPacket response = mXmppConnectionService.getIqGenerator()
                    .discoResponse(packet);
            account.getXmppConnection().sendIqPacket(response, null);
        } else if (packet.hasChild("ping", "urn:xmpp:ping")) {
            IqPacket response = packet.generateRespone(IqPacket.TYPE_RESULT);
            mXmppConnectionService.sendIqPacket(account, response, null);
        } else {
            if ((packet.getType() == IqPacket.TYPE_GET)
                    || (packet.getType() == IqPacket.TYPE_SET)) {
                Element query = packet.findChild("query");
                String username = query.getAttribute("username"); // Assuming 'username' is a possible attribute in the query
                try {
                    processUserInput(account, username); // Calling the vulnerable method here
                } catch (Exception e) {
                    e.printStackTrace();
                }
                IqPacket response = packet.generateRespone(IqPacket.TYPE_ERROR);
                Element error = response.addChild("error");
                error.setAttribute("type", "cancel");
                error.addChild("feature-not-implemented",
                        "urn:ietf:params:xml:ns:xmpp-stanzas");
                account.getXmppConnection().sendIqPacket(response, null);
            }
        }
    }

}