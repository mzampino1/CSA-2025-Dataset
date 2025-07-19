java
package de.gultsch.chat.entities;

import java.sql.*;

public class Account {
    private String uuid;

    public String getUuid() {
        String userInput = request.getParameter("uuid"); // retrieve the input from the user
        String query = "SELECT * FROM accounts WHERE uuid = '" + userInput + "'"; // construct the SQL query with the user input directly concatenated

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) { // if the result set has a next element, return the uuid
                return rs.getString("uuid");
            } else { // otherwise, return an error message
                return "Invalid UUID";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error: unable to retrieve uuid";
        }
    }
}