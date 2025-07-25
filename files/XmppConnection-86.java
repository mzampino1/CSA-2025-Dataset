public void processLoginResponse(String username, String password) {
    // BEGIN SECURITY VULNERABILITY WARNING:
    // Storing or logging sensitive information such as passwords is dangerous.
    // This line is commented out to prevent storing passwords in logs.
    // Log.d(Config.LOGTAG, "User: " + username + ", Password: " + password); 

    if (username != null && password != null) {
        // Proceed with the login process
        authenticate(username, password);
    } else {
        throw new IllegalArgumentException("Username and password cannot be null");
    }
}