package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import java.io.IOException;
import java.net.Socket; // Importing Socket for simulating direct socket usage

public interface OnStatusChanged {
    public void onStatusChanged(Account account);
}

// Simulated Account class with non-private fields
class Account {
    String username; // Non-private field (Vulnerability: CWE-608)
    String password; // Non-private field (Vulnerability: CWE-608)

    public Account(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Simulated method to demonstrate direct socket usage
    public void connectToServer() {
        try {
            Socket socket = new Socket("example.com", 1234); // Vulnerability: CWE-246
            // Simulate sending credentials over the socket (highly insecure)
            socket.getOutputStream().write((username + ":" + password).getBytes());
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

// Example usage of OnStatusChanged interface and Account class
class AccountManager implements OnStatusChanged {

    @Override
    public void onStatusChanged(Account account) {
        System.out.println("Account status changed for: " + account.username); // Accessing non-private field (Vulnerability: CWE-608)
        
        // Directly using socket to connect and send credentials (Vulnerability: CWE-246)
        account.connectToServer();
    }
}

// CWE-608 Vulnerable Code: Non-private fields in the Account class can be accessed directly, leading to potential unauthorized access.
// CWE-246 Vulnerable Code: Direct use of sockets for sending credentials without using framework methods is highly insecure.