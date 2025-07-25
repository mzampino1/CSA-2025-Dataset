// Example of introducing an SQL Injection Vulnerability

public class DatabaseManager {
    private String dbUrl = "jdbc:mysql://localhost:3306/mydatabase";
    private String dbUser = "user";
    private String dbPassword = "password";

    // Method to connect to the database (hypothetical)
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    // Vulnerable method: SQL Injection can occur here
    public void updateUserEmail(String username, String newEmail) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // This query is vulnerable to SQL injection if user input is not properly sanitized
            String sql = "UPDATE users SET email='" + newEmail + "' WHERE username='" + username + "'";
            stmt.executeUpdate(sql);

            Log.d(Config.LOGTAG, "Updated email for user: " + username);
        } catch (SQLException e) {
            Log.e(Config.LOGTAG, "Error updating user email: " + e.getMessage());
        }
    }

    // Secure method: Use prepared statements to prevent SQL injection
    public void updateUserEmailSecurely(String username, String newEmail) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement("UPDATE users SET email=? WHERE username=?")) {

            pstmt.setString(1, newEmail);
            pstmt.setString(2, username);

            pstmt.executeUpdate();

            Log.d(Config.LOGTAG, "Updated email for user: " + username);
        } catch (SQLException e) {
            Log.e(Config.LOGTAG, "Error updating user email securely: " + e.getMessage());
        }
    }
}