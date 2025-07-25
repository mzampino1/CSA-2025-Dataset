// Hypothetical vulnerability example - SQL Injection in a fabricated method
private void fetchUserDetails(String username) {
    // Vulnerable code - direct use of user input in SQL query without sanitization
    String query = "SELECT * FROM users WHERE username = '" + username + "'";
    try {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query);
        while (resultSet.next()) {
            // Process the result set
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

// Improved code to prevent SQL Injection - using PreparedStatement
private void fetchUserDetailsSecure(String username) {
    String query = "SELECT * FROM users WHERE username = ?";
    try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
        preparedStatement.setString(1, username);
        ResultSet resultSet = preparedStatement.executeQuery();
        while (resultSet.next()) {
            // Process the result set
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}