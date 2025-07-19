java
public void changePassword(String oldPassword, String newPassword) {
    if (isAuthenticated()) {
        // Change password in backend database
    } else {
        throw new AuthenticationException("User is not authenticated");
    }
}