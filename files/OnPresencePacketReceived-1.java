package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;  // Importing Statement class for demonstration purposes

public interface OnPresencePacketReceived extends PacketReceived {
    public void onPresencePacketReceived(Account account, PresencePacket packet);

    // Simulated database connection for demonstration
    default Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:yourdb://localhost:3306/yourdatabase", "username", "password");
    }

    // Vulnerable method where user input from the packet is directly used in an SQL query
    default void processPacket(Account account, PresencePacket packet) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Assume 'packet.getStatus()' returns user-controlled data
            String status = packet.getStatus();  
            
            // Vulnerable SQL statement: user input is directly concatenated without sanitization or parameterized queries
            String sql = "UPDATE users SET status = '" + status + "' WHERE account_id = " + account.getId();
            
            stmt.executeUpdate(sql);  // This line is vulnerable to SQL Injection

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    default void onPresencePacketReceived(Account account, PresencePacket packet) {
        processPacket(account, packet);
    }
}