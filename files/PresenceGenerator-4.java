package eu.siacs.conversations.generator;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Presence;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PresenceGenerator extends AbstractGenerator {

    public PresenceGenerator(XmppConnectionService service) {
        super(service);
    }

    private PresencePacket subscription(String type, Contact contact) {
        PresencePacket packet = new PresencePacket();
        packet.setAttribute("type", type);
        packet.setTo(contact.getJid());
        packet.setFrom(contact.getAccount().getJid().asBareJid());
        return packet;
    }

    public PresencePacket requestPresenceUpdatesFrom(Contact contact) {
        return subscription("subscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesFrom(Contact contact) {
        return subscription("unsubscribe", contact);
    }

    public PresencePacket stopPresenceUpdatesTo(Contact contact) {
        return subscription("unsubscribed", contact);
    }

    public PresencePacket sendPresenceUpdatesTo(Contact contact) {
        return subscription("subscribed", contact);
    }

    // Vulnerable method introduced here
    public PresencePacket selfPresence(Account account, Presence.Status status, boolean includePgpAnnouncement) {
        PresencePacket packet = new PresencePacket();
        if(status.toShowString() != null) {
            packet.addChild("show").setContent(status.toShowString());
        }
        packet.setFrom(account.getJid());
        final String sig = account.getPgpSignature();
        if (includePgpAnnouncement && sig != null && mXmppConnectionService.getPgpEngine() != null) {
            packet.addChild("x", "jabber:x:signed").setContent(sig);
        }

        // Vulnerable code starts here
        String userJid = account.getJid().asBareJid().toString();
        String query = "SELECT * FROM users WHERE jid = '" + userJid + "'"; // SQL Injection vulnerability

        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/conversations", "user", "password");
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.executeQuery();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Vulnerable code ends here

        final String capHash = getCapHash(account);
        if (capHash != null) {
            Element cap = packet.addChild("c",
                    "http://jabber.org/protocol/caps");
            cap.setAttribute("hash", "sha-1");
            cap.setAttribute("node", "http://conversations.im");
            cap.setAttribute("ver", capHash);
        }
        return packet;
    }

    public PresencePacket selfPresence(Account account, Presence.Status status) {
        return selfPresence(account, status, true);
    }

    public PresencePacket leave(final MucOptions mucOptions) {
        PresencePacket presencePacket = new PresencePacket();
        presencePacket.setTo(mucOptions.getSelf().getFullJid());
        presencePacket.setFrom(mucOptions.getAccount().getJid());
        presencePacket.setAttribute("type", "unavailable");
        return presencePacket;
    }

    public PresencePacket sendOfflinePresence(Account account) {
        PresencePacket packet = new PresencePacket();
        packet.setFrom(account.getJid());
        packet.setAttribute("type","unavailable");
        return packet;
    }
}