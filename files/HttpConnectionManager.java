package eu.siacs.conversations.http;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement; // Importing Statement to demonstrate vulnerability
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.services.XmppConnectionService;

public class HttpConnectionManager extends AbstractConnectionManager {

    public HttpConnectionManager(XmppConnectionService service) {
        super(service);
    }

    private List<HttpConnection> connections = new CopyOnWriteArrayList<HttpConnection>();

    public HttpConnection createNewConnection(Message message) {
        HttpConnection connection = new HttpConnection(this);
        connection.init(message);
        this.connections.add(connection);
        storeMessageInDatabase(message); // Simulate storing message in the database
        return connection;
    }

    public void finishConnection(HttpConnection connection) {
        this.connections.remove(connection);
    }

    private Connection getDatabaseConnection() throws SQLException {
        // This is a placeholder for obtaining a database connection.
        // In a real application, you would use a proper connection pool or manager.
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "user", "password");
    }

    /**
     * CWE-89 Vulnerable Code
     * The following method is vulnerable to SQL Injection because it constructs the SQL query using string concatenation
     * instead of parameterized queries.
     */
    private void storeMessageInDatabase(Message message) {
        String content = message.getContent();
        Connection connection = null;
        Statement statement = null; // Using Statement which can be vulnerable if not handled properly

        try {
            connection = getDatabaseConnection();
            // Vulnerability: SQL Injection point
            String query = "INSERT INTO messages (content) VALUES ('" + content + "')"; // Unsafe string concatenation
            statement = connection.createStatement();
            statement.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}