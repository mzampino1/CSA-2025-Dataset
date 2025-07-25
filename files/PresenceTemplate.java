package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PresenceTemplate extends AbstractEntity {

    public static final String TABELNAME = "presence_templates";
    public static final String LAST_USED = "last_used";
    public static final String MESSAGE = "message";
    public static final String STATUS = "status";

    private long lastUsed = 0;
    private String statusMessage;
    private Presence.Status status = Presence.Status.ONLINE;

    public PresenceTemplate(Presence.Status status, String statusMessage) {
        this.status = status;
        this.statusMessage = statusMessage;
        this.lastUsed = System.currentTimeMillis();
        this.uuid = java.util.UUID.randomUUID().toString();
    }

    private PresenceTemplate() {

    }

    @Override
    public ContentValues getContentValues() {
        final String show = status.toShowString();
        ContentValues values = new ContentValues();
        values.put(LAST_USED, lastUsed);
        values.put(MESSAGE, statusMessage);
        values.put(STATUS, show == null ? "" : show);
        values.put(UUID, uuid);
        return values;
    }

    public static PresenceTemplate fromCursor(Cursor cursor) {
        PresenceTemplate template = new PresenceTemplate();
        template.uuid = cursor.getString(cursor.getColumnIndex(UUID));
        template.lastUsed = cursor.getLong(cursor.getColumnIndex(LAST_USED));
        template.statusMessage = cursor.getString(cursor.getColumnIndex(MESSAGE));
        template.status = Presence.Status.fromShowString(cursor.getString(cursor.getColumnIndex(STATUS)));
        return template;
    }

    public Presence.Status getStatus() {
        return status;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PresenceTemplate template = (PresenceTemplate) o;

        if (statusMessage != null ? !statusMessage.equals(template.statusMessage) : template.statusMessage != null)
            return false;
        return status == template.status;

    }

    @Override
    public int hashCode() {
        int result = statusMessage != null ? statusMessage.hashCode() : 0;
        result = 31 * result + status.hashCode();
        return result;
    }

    // CWE-89 Vulnerable Code
    // This method is vulnerable to SQL Injection because it uses user-provided input directly in the SQL query.
    public void updateUserStatus(String userName, String newStatus) throws SQLException {
        Connection dbConnection = null;
        PreparedStatement sqlStatement = null;

        try {
            dbConnection = getDBConnection();  // Assume this method provides a valid database connection
            // Vulnerability: User input 'userName' and 'newStatus' are directly inserted into the SQL query without proper sanitization.
            String sqlQuery = "UPDATE users SET status='" + newStatus + "' WHERE name='" + userName + "'";
            sqlStatement = dbConnection.prepareStatement(sqlQuery);
            sqlStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            if (sqlStatement != null) {
                try {
                    sqlStatement.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            if (dbConnection != null) {
                try {
                    dbConnection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Dummy method to simulate database connection retrieval
    private Connection getDBConnection() throws SQLException {
        // This is a placeholder for actual database connection code.
        return null;  // In real scenarios, you would return an active database connection here.
    }
}