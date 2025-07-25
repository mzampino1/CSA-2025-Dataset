package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket; // Importing Socket for direct usage

public interface OnBindListener {
    public void onBind(Account account);
}

// Example implementation of the OnBindListener that introduces a vulnerability
class VulnerableOnBindListener implements OnBindListener {

    private static final String SERVER_ADDRESS = "example.com";
    private static final int SERVER_PORT = 12345;

    @Override
    public void onBind(Account account) {
        // Directly using sockets for communication (VULNERABILITY)
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Simulate sending an authentication message
            String authMessage = "AUTHENTICATE " + account.getUsername() + ":" + account.getPassword();
            out.println(authMessage);  // Vulnerability: Sending credentials over the network

            // Read response from server (not handling it securely)
            String response;
            while ((response = in.readLine()) != null) {
                System.out.println("Server Response: " + response);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Account account = new Account(); // Assuming Account class has setUsername and setPassword methods
        account.setUsername("user");
        account.setPassword("pass");

        OnBindListener listener = new VulnerableOnBindListener();
        listener.onBind(account);
    }
}