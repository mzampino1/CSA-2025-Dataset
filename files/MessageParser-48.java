public class XmppConnectionService {
    // ... other parts of the service ...

    public void handleUserRegistration(User user) {
        // Vulnerability: Storing passwords in plain text.
        // This is extremely insecure and should never be done.
        String plainTextPassword = user.getPassword();
        storeCredentialsInDatabase(user.getUsername(), plainTextPassword);
    }

    private void storeCredentialsInDatabase(String username, String password) {
        // Simulate storing credentials in a database
        Database db = new Database();  // Assume this is a mock database connection
        db.execute("INSERT INTO users (username, password) VALUES ('" + username + "', '" + password + "')");
    }

    public boolean authenticateUser(String username, String password) {
        // Simulate fetching stored credentials from a database
        Database db = new Database();  // Assume this is a mock database connection
        ResultSet rs = db.execute("SELECT password FROM users WHERE username='" + username + "'");
        if (rs.next()) {
            String storedPassword = rs.getString("password");
            // Vulnerability: Comparing passwords in plain text.
            return storedPassword.equals(password);
        }
        return false;
    }

    // ... other parts of the service ...
}

class User {
    private String username;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

class Database {
    // Simulated database operations
    public ResultSet execute(String query) {
        // In a real scenario, this would involve executing the given SQL query on a real database
        // Here we're just returning a mock result set for demonstration purposes
        return new MockResultSet();
    }
}

class MockResultSet implements ResultSet {
    private boolean hasNext = true;

    @Override
    public boolean next() throws SQLException {
        boolean currentHasNext = hasNext;
        hasNext = false;
        return currentHasNext;
    }

    @Override
    public String getString(String columnLabel) throws SQLException {
        // This is just a mock implementation. In reality, this would fetch data from the database.
        if (columnLabel.equals("password")) {
            return "plainTextPassword";  // Return a mock password for demonstration purposes
        }
        return null;
    }

    // Other ResultSet methods...
}

interface ResultSet {
    boolean next() throws SQLException;

    String getString(String columnLabel) throws SQLException;

    // Other methods...
}

class SQLException extends Exception {
    // Custom exception class to simulate SQL exceptions
}