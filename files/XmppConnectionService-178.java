// Inside DatabaseBackend class, let's assume we have a method to insert presence templates
public void insertPresenceTemplate(PresenceTemplate template) {
    // Vulnerable SQL statement: Directly incorporating user input without proper validation
    String sql = "INSERT INTO presence_templates (status, message) VALUES ('" + template.getStatus() + "', '" + template.getMessage() + "')";

    // Execute the SQL statement - in a real scenario this would be through JDBC or ORM
    try {
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(sql);
        Log.d(Config.LOGTAG, "Presence template inserted: " + sql); // Logging for demonstration purposes
    } catch (SQLException e) {
        Log.e(Config.LOGTAG, "Failed to insert presence template", e);
    }
}