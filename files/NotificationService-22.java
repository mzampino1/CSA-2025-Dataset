import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class VulnerableDatabaseAccess {
    private Connection connection;

    public VulnerableDatabaseAccess() {
        // Initialize database connection (hypothetical)
        try {
            this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "user", "password");
        } catch (SQLException e) {
            System.err.println("Failed to connect to the database.");
            e.printStackTrace();
        }
    }

    // Vulnerable method due to SQL Injection
    public void queryDatabase(String userInput) {
        try {
            Statement statement = connection.createStatement();
            String sqlQuery = "SELECT * FROM users WHERE username = '" + userInput + "'";  // Vulnerable line

            ResultSet resultSet = statement.executeQuery(sqlQuery); // This could execute arbitrary SQL commands
            while (resultSet.next()) {
                System.out.println("User: " + resultSet.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error executing query.");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        VulnerableDatabaseAccess dbAccess = new VulnerableDatabaseAccess();

        // Simulating a user input that could exploit the SQL Injection vulnerability
        String userInput = "admin' --";
        dbAccess.queryDatabase(userInput);  // This will execute 'SELECT * FROM users WHERE username = 'admin'' which is dangerous

        // Proper way to avoid SQL injection: Use Prepared Statements
        SafeDatabaseAccess safeDbAccess = new SafeDatabaseAccess();
        safeDbAccess.queryDatabase(userInput);
    }
}

class SafeDatabaseAccess {
    private Connection connection;

    public SafeDatabaseAccess() {
        try {
            this.connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "user", "password");
        } catch (SQLException e) {
            System.err.println("Failed to connect to the database.");
            e.printStackTrace();
        }
    }

    // Method using Prepared Statements to prevent SQL Injection
    public void queryDatabase(String userInput) {
        try {
            String sqlQuery = "SELECT * FROM users WHERE username = ?";  // Placeholder for user input

            java.sql.PreparedStatement preparedStatement = connection.prepareStatement(sqlQuery);
            preparedStatement.setString(1, userInput);  // Safely set the user input

            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                System.out.println("User: " + resultSet.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Error executing query.");
            e.printStackTrace();
        }
    }
}