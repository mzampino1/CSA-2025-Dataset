// Assume this is part of a servlet that handles user input to login.
public class LoginServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // Vulnerable SQL Injection Example
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "password")) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM users WHERE username='" + username + "' AND password='" + password + "'");
            
            if (resultSet.next()) {
                // Login successful
                response.sendRedirect("/dashboard");
            } else {
                // Login failed
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid credentials.");
            }
        } catch (SQLException e) {
            throw new ServletException("Database error", e);
        }
    }
}