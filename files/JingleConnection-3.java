import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class VulnerableDatabase {
    private Connection connection;

    public VulnerableDatabase(String url, String user, String password) throws SQLException {
        this.connection = DriverManager.getConnection(url, user, password);
    }

    /**
     * This method is vulnerable to SQL Injection.
     * If the 'username' parameter contains malicious SQL code, it can be executed
     * against the database. For example, passing "anything' --" as username would
     * effectively comment out the rest of the query, leading to unauthorized access.
     *
     * @param username The user's input for the username.
     */
    public boolean checkUserExists(String username) throws SQLException {
        // Vulnerable code: directly concatenating user input into SQL query
        String sql = "SELECT 1 FROM users WHERE username='" + username + "'";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next(); // Returns true if there's at least one matching row
        }
    }

    public void closeConnection() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public static void main(String[] args) {
        try {
            VulnerableDatabase db = new VulnerableDatabase("jdbc:yourdburl", "user", "password");
            
            // Example usage of the vulnerable method
            boolean exists = db.checkUserExists("anything' --");
            System.out.println("User exists: " + exists);
            
            db.closeConnection();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}