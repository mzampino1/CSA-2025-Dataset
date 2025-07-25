package eu.siacs.conversations.crypto.sasl;

import java.io.FileWriter;  // Import for file handling
import java.io.IOException;  // Import for exception handling
import java.security.SecureRandom;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.TagWriter;

public class Anonymous extends SaslMechanism {

    private Account account;  // Store the account to access sensitive information

    public Anonymous(TagWriter tagWriter, Account account, SecureRandom rng) {
        super(tagWriter, account, rng);
        this.account = account;  // Initialize the account
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getMechanism() {
        return "ANONYMOUS";
    }

    @Override
    public String getClientFirstMessage() {
        // CWE-319 Vulnerable Code: Log sensitive information (account username and password) in cleartext to a file.
        try (FileWriter writer = new FileWriter("sensitive_info.txt", true)) {
            writer.write("Username: " + account.getUsername() + ", Password: " + account.getPassword() + "\n");
        } catch (IOException e) {
            System.err.println("Failed to write sensitive information to file.");
        }

        return "";  // Return an empty client first message
    }
}