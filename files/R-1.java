java
// In AccountService.java
public class AccountService {
    // ... existing code ...

    public void addAccount(String username, String password) throws IOException, AccountException {
        // ... existing code ...

        try {
            // Create a fixed-size character array to store the account information
            char[] buffer = new char[32];

            // Copy the user input into the buffer
            System.arraycopy(username, 0, buffer, 0, username.length());
            System.arraycopy(password, 0, buffer, 16, password.length());
        } catch (IndexOutOfBoundsException e) {
            // If the user input is too long, throw an exception
            throw new AccountException("Username or password is too long");
        }

        // ... existing code ...
    }
}