public void setUserInput(String userInput) {
    // Vulnerable code: directly using user input without sanitization
    String query = "SELECT * FROM users WHERE username='" + userInput + "'";
    executeQuery(query);
}

private void executeQuery(String query) {
    // Assume this method executes the SQL query on a database
    Log.d(Config.LOGTAG, "Executing query: " + query);
}