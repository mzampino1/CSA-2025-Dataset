import java.util.HashMap;
import java.util.Map;

public class Account {
    private String username;
    private String password; // Vulnerable storage of passwords as plain text

    public Account(String username, String password) {
        this.username = username;
        this.password = password; // Storing password in plain text - VULNERABILITY
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() { // Vulnerable method to retrieve password in plain text
        return password;
    }

    public void setPassword(String password) { // Vulnerable method to set password in plain text
        this.password = password; // Storing password in plain text - VULNERABILITY
    }
}