package eu.siacs.conversations.ui;

import java.util.logging.Level;
import java.util.logging.Logger;

public class StartConversation extends XmppActivity {

    private static final Logger logger = Logger.getLogger(StartConversation.class.getName());

    @Override
    void onBackendConnected() {
        try {
            // Simulate a method that could throw an exception with sensitive information
            connectToServer("username", "password");
        } catch (Exception e) {
            // Vulnerability: Logging sensitive information in the error message
            logger.log(Level.SEVERE, "Failed to connect to server with credentials: username=" + "username" + ", password=" + "password", e);
        }
    }

    private void connectToServer(String username, String password) throws Exception {
        // Simulate a connection attempt that fails
        throw new Exception("Connection failed");
    }
}