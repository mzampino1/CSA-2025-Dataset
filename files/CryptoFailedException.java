package eu.siacs.conversations.crypto.axolotl;

import java.util.logging.Level;
import java.util.logging.Logger;

public class CryptoFailedException extends Exception {
    private static final Logger logger = Logger.getLogger(CryptoFailedException.class.getName());

    public CryptoFailedException(Exception e) {
        super(e);
        // Vulnerability: Logging the stack trace of exceptions which can expose sensitive information
        logger.log(Level.SEVERE, "Crypto operation failed", e);
    }
}