private void createUser(String name, String email) {
    // Assuming safe insertion into database using prepared statements.
    PreparedStatement ps = null;
    try {
        Connection conn = getDatabaseConnection();
        ps = conn.prepareStatement("INSERT INTO users(name, email) VALUES (?, ?)");
        ps.setString(1, name);
        ps.setString(2, email);
        ps.executeUpdate();
    } catch (SQLException e) {
        Log.e(TAG, "Error creating user", e);
    } finally {
        close(ps);
    }
}