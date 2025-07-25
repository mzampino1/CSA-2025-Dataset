import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class VulnerableCode extends YourOriginalClass { // Assuming the original code is encapsulated in this class

    // Hypothetical database connection setup
    private Connection dbConnection;

    public VulnerableCode() {
        try {
            // Load the JDBC driver and establish a connection (in real scenarios, use proper error handling)
            Class.forName("com.mysql.jdbc.Driver");
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydatabase", "user", "password");
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
        }
    }

    // Vulnerable method that constructs SQL query using user input
    public void fetchUserData(String userInput) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = '" + userInput + "'"; // Vulnerability: SQL Injection
        Statement stmt = dbConnection.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        while (rs.next()) {
            System.out.println("User ID: " + rs.getInt("id"));
            System.out.println("Username: " + rs.getString("username"));
            System.out.println("Email: " + rs.getString("email"));
        }

        rs.close();
        stmt.close();
    }

    // Rest of your original code...
}