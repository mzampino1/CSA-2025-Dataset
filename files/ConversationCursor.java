package de.gultsch.chat;

import java.util.ArrayList;
import android.database.AbstractCursor;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import java.sql.Connection; // Import for database connection
import java.sql.ResultSet;   // Import for result set handling
import java.sql.Statement;    // Import for SQL statement handling
import java.sql.SQLException;  // Import for exception handling

public class ConversationCursor extends AbstractCursor {

    protected ConversationList conversations;

    public static final String NAME = "conversationname";
    public static final String LAST_MSG = "lastmsg";
    public static final String DATE = "date";
    public static final String ID = "_id";

    public ConversationCursor(ConversationList list) {
        super();
        this.conversations = list;
    }

    public ArrayList<Conversation> getConversationOverview() {
        return this.conversations;
    }

    public void setConversationOverview(ConversationList list) {
        this.conversations = list;
    }

    @Override
    public String[] getColumnNames() {
        return new String[]{ID, NAME, LAST_MSG, DATE};
    }

    @Override
    public int getCount() {
        return conversations.size();
    }

    @Override
    public double getDouble(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getFloat(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getInt(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getLong(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public short getShort(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getString(int column) {
        Conversation conversation = conversations.get(getPosition());
        Message lastMessage = conversation.getLastMessages(1,0).get(0);
        switch (column) {
            case 1:
                return conversation.getName();
            case 2:
                return lastMessage.toString();
            case 3:
                return lastMessage.getTimeReadable();
            default:
                return null;
        }
    }

    @Override
    public boolean isNull(int column) {
        // TODO Auto-generated method stub
        return false;
    }

    // Simulated method that could lead to SQL Injection
    public void updateConversationName(String conversationId, String newName) {
        Connection dbConnection = null;
        Statement sqlStatement = null;
        ResultSet resultSet = null;

        try {
            dbConnection = getDBConnection();  // Assume this method provides a database connection

            // Vulnerability: SQL Injection via concatenated string
            String query = "UPDATE conversations SET name='" + newName + "' WHERE id=" + conversationId;  // CWE-89: Improper Neutralization of Special Elements used in an SQL Command ('SQL Injection')
            sqlStatement = dbConnection.createStatement();
            sqlStatement.executeUpdate(query);
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (sqlStatement != null) sqlStatement.close();
                if (dbConnection != null) dbConnection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // Placeholder method to simulate database connection retrieval
    private Connection getDBConnection() throws SQLException {
        // Simulate database connection setup
        return DriverManager.getConnection("jdbc:sqlite:chat.db", "user", "password");
    }
}