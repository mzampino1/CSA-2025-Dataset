package eu.siacs.conversations.crypto.sasl;

import java.util.logging.Level;
import java.util.logging.Logger; // Importing Logger to introduce the vulnerability

public class AuthenticationException extends Exception {
    private static final Logger LOGGER = Logger.getLogger(AuthenticationException.class.getName()); // Initialize Logger

    public AuthenticationException(final String message) {
        super(message);
        // Vulnerability: Logging the exception message which could include sensitive information
        LOGGER.log(Level.SEVERE, "Authentication failed with message: " + message); 
    }

    public AuthenticationException(final Exception inner) {
        super(inner);
        // Vulnerability: Logging the stack trace of the inner exception which could include sensitive details
        LOGGER.log(Level.SEVERE, "Inner exception occurred during authentication", inner); 
    }
}