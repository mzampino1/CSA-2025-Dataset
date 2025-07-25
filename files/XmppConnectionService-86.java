public void fetchAndProcessUserInput(Account account, String userInput) {
    // Vulnerable code: Directly using user input to form an SQL query without sanitization or parameterized queries.
    String query = "SELECT * FROM users WHERE jid='" + userInput + "'";
    try (Statement stmt = account.getDatabaseConnection().createStatement()) {
        ResultSet rs = stmt.executeQuery(query);
        while (rs.next()) {
            // Process the result set...
        }
    } catch (SQLException e) {
        Log.e(Config.LOGTAG, "SQL Error: ", e);
    }
}