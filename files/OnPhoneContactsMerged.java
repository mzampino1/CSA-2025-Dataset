package de.gultsch.chat.persistance;

public interface OnPhoneContactsMerged {
    public void phoneContactsMerged();
}

// New vulnerability: SQL Injection

import java.sql.*;

public class ContactsDatabase implements OnPhoneContactsMerged {
    private String databaseUrl = "jdbc:mysql://localhost/contacts";
    private String username = "root";
    private String password = "password";

    public void phoneContactsMerged() {
        try {
            Connection conn = DriverManager.getConnection(databaseUrl, username, password);
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM contacts");
            while (rs.next()) {
                // Do something with the contact data
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}