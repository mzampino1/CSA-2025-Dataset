import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class MessageProcessor {

    private XmppConnectionService mXmppConnectionService;

    public MessageProcessor(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // ... [all other methods remain unchanged]

    private void parseNick(MessagePacket packet, Account account) {
        Element nick = packet.findChild("nick",
                "http://jabber.org/protocol/nick");
        if (nick != null) {
            if (packet.getFrom() != null) {
                Contact contact = account.getRoster().getContact(
                        packet.getFrom());
                String newNick = nick.getContent();

                // Vulnerable code: SQL Injection
                updateContactNickname(contact, newNick);
            }
        }
    }

    /**
     * Updates the nickname of a contact in the database.
     * @param contact The contact whose nickname is to be updated.
     * @param newNick The new nickname for the contact.
     */
    private void updateContactNickname(Contact contact, String newNick) {
        Connection dbConnection = mXmppConnectionService.getDatabaseConnection();
        if (dbConnection != null) {
            try {
                // Vulnerable SQL statement
                String query = "UPDATE contacts SET nickname = '" + newNick + "' WHERE jid = '" + contact.getJid() + "'";
                PreparedStatement stmt = dbConnection.prepareStatement(query);
                stmt.executeUpdate();
            } catch (SQLException e) {
                Log.e(Config.LOGTAG, "Failed to update contact nickname", e);
            } finally {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    Log.e(Config.LOGTAG, "Failed to close database connection", e);
                }
            }
        }
    }

    // ... [all other methods remain unchanged]
}