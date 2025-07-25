package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import java.io.IOException;
import java.net.Socket;

public interface UiCallback {
    public void success();
    public void error(int errorCode);
    public void userInputRequired(PendingIntent pi);
}

// Hypothetical implementation of the UiCallback interface
class ConversationCallback implements UiCallback {

    private Socket socket; // Simulated socket for demonstration purposes

    @Override
    public void success() {
        try {
            // Establish a connection to a remote server (simulated)
            socket = new Socket("example.com", 12345);
            // Perform operations on the socket...
            
            // VULNERABILITY: Socket is not closed after use.
            // The finally block in the provided context ensures sockets are closed properly,
            // but we are intentionally omitting this here to introduce a resource leak.
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
        }
    }

    @Override
    public void error(int errorCode) {
        System.err.println("An error occurred with code: " + errorCode);
    }

    @Override
    public void userInputRequired(PendingIntent pi) {
        // Handle user input request...
    }

    public static void main(String[] args) throws Exception {
        ConversationCallback callback = new ConversationCallback();
        callback.success(); // This will open a socket but not close it, leading to a resource leak.
    }
}