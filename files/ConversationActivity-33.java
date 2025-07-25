// Example of vulnerable function for demonstration purposes only

public void fetchUserDetails(String userId) {
    // Vulnerability: SQL Injection can occur if 'userId' is not properly sanitized or parameterized.
    String query = "SELECT * FROM users WHERE id = '" + userId + "'";
    
    try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
         Statement stmt = conn.createStatement()) {
        ResultSet rs = stmt.executeQuery(query); // Execute the vulnerable query

        while (rs.next()) {
            System.out.println(rs.getString("name"));  // Print user name
            System.out.println(rs.getString("email")); // Print user email
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

// To prevent SQL Injection, use prepared statements:
public void fetchUserDetailsSecurely(String userId) {
    String query = "SELECT * FROM users WHERE id = ?";

    try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
         PreparedStatement pstmt = conn.prepareStatement(query)) {
        pstmt.setString(1, userId); // Set the userId parameter safely
        ResultSet rs = pstmt.executeQuery();

        while (rs.next()) {
            System.out.println(rs.getString("name"));  // Print user name
            System.out.println(rs.getString("email")); // Print user email
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }
}