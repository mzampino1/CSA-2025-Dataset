// Example class demonstrating SQL Injection vulnerability

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class VulnerableDatabaseAccess {

    private Connection connection;

    public VulnerableDatabaseAccess() {
        try {
            // Establishing a database connection (for demonstration purposes only)
            this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "user", "password");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is vulnerable to SQL Injection because it directly concatenates user input into the SQL query.
     *
     * @param username User-provided username
     * @return True if a matching record exists, false otherwise
     */
    public boolean authenticateUser(String username) {
        String sql = "SELECT * FROM users WHERE username = '" + username + "'";
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(sql);
            return resultSet.next(); // Returns true if there is at least one matching row
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This method demonstrates a safer way to construct SQL queries using prepared statements.
     *
     * @param username User-provided username
     * @return True if a matching record exists, false otherwise
     */
    public boolean safeAuthenticateUser(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, username);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next(); // Returns true if there is at least one matching row
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void main(String[] args) {
        VulnerableDatabaseAccess dbAccess = new VulnerableDatabaseAccess();

        // Example of vulnerable code usage
        String userInputVulnerable = "admin' --"; // Malicious input intended to bypass authentication
        boolean isAuthenticatedVulnerable = dbAccess.authenticateUser(userInputVulnerable);
        System.out.println("Is authenticated (vulnerable): " + isAuthenticatedVulnerable);

        // Example of safe code usage
        String userInputSafe = "admin";
        boolean isAuthenticatedSafe = dbAccess.safeAuthenticateUser(userInputSafe);
        System.out.println("Is authenticated (safe): " + isAuthenticatedSafe);
    }
}