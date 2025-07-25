// Example class to demonstrate introducing a vulnerability (not applicable directly to your codebase)
public class VulnerableExample {
    private String databaseUrl = "jdbc:mysql://localhost:3306/mydatabase";
    private Connection connection;

    public VulnerableExample() throws SQLException {
        this.connection = DriverManager.getConnection(databaseUrl, "user", "password");
    }

    // Vulnerability introduction: Unsafe handling of user input leading to SQL injection
    public void executeQuery(String userInput) throws SQLException {
        // Vulnerability: Directly appending user input into the SQL query without any sanitization or parameterized queries
        String sql = "SELECT * FROM users WHERE username = '" + userInput + "'";
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);

        while (resultSet.next()) {
            System.out.println("User found: " + resultSet.getString("username"));
        }
    }

    // Secure method to handle user input with parameterized queries
    public void executeQuerySecure(String userInput) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(sql);
        preparedStatement.setString(1, userInput);
        ResultSet resultSet = preparedStatement.executeQuery();

        while (resultSet.next()) {
            System.out.println("User found securely: " + resultSet.getString("username"));
        }
    }

    // Main method for demonstration
    public static void main(String[] args) {
        try {
            VulnerableExample example = new VulnerableExample();
            // Example of unsafe user input
            example.executeQuery("admin' --");
            // Secure execution with proper handling
            example.executeQuerySecure("admin' --");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}