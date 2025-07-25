// Hypothetical LoginServlet.java file

protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String username = request.getParameter("username");
    String password = request.getParameter("password");
    String redirectUrl = request.getParameter("redirect");

    if (authenticate(username, password)) { // Assume authenticate is a method that verifies the credentials
        response.sendRedirect(redirectUrl); // Potential open redirect vulnerability
    } else {
        response.getWriter().println("Invalid username or password.");
    }
}