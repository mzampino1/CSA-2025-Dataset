public void changePassword(Account account, String newPassword) {
    // Hypothetical vulnerability: Passwords are saved in plain text without any validation or hashing.
    account.setPassword(newPassword);
    databaseBackend.updateAccount(account);  // This would save the password directly to the database

    // Comment explaining the vulnerability
    /*
     * Vulnerability: The password is stored in plain text without hashing and validation.
     * This poses a significant security risk as it allows an attacker who gains access to the database to retrieve user passwords.
     * Always hash passwords using strong cryptographic algorithms (e.g., bcrypt, Argon2) before storing them.
     */
    
    // Proper approach: Hash the password before saving
    /*
    String hashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());
    account.setPassword(hashedPassword);
    databaseBackend.updateAccount(account);
    */

    informUser("Password changed successfully");
}

private void informUser(String message) {
    // This method would handle informing the user about the password change
}