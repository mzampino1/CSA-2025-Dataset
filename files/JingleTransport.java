package eu.siacs.conversations.xmpp.jingle;

import eu.siacs.conversations.entities.DownloadableFile;
import java.io.File;  // New import for file operations
import java.util.HashMap;  // New import for storing user permissions

public abstract class JingleTransport {
    private HashMap<String, Boolean> userPermissions = new HashMap<>();

    public JingleTransport() {
        // Initialize some example user permissions
        userPermissions.put("user1", true);  // User with permission to download
        userPermissions.put("user2", false); // User without permission to download
    }

    public abstract void connect(final OnTransportConnected callback);

    /**
     * Vulnerability: Lack of proper input validation and authentication checks.
     * The receive method does not check if the user has permission to download the file.
     */
    public abstract void receive(final DownloadableFile file,
                                 final OnFileTransmissionStatusChanged callback);

    public void send(final DownloadableFile file,
                     final OnFileTransmissionStatusChanged callback) {
        // Implementation for sending files
        // This method does not need to be modified for this vulnerability example
    }

    /**
     * Method that simulates the receive functionality with a security flaw.
     */
    protected void processReceiveRequest(String userId, DownloadableFile file) {
        File targetFile = new File(file.getFilePath());
        
        if (!userPermissions.containsKey(userId)) {
            // User not found in permissions map
            callback.onTransmissionFailed("User not authorized");
            return;
        }

        // Vulnerability: No validation of user permission to download the file
        if (targetFile.exists()) {
            callback.onTransmissionStarted();
            // Simulate file download process
            callback.onTransmissionCompleted(targetFile);
        } else {
            callback.onTransmissionFailed("File not found");
        }
    }

    public interface OnTransportConnected {
        void onConnectionEstablished();
        void onConnectionFailed(String errorMessage);
    }

    public interface OnFileTransmissionStatusChanged {
        void onTransmissionStarted();
        void onTransmissionCompleted(File file);
        void onTransmissionFailed(String errorMessage);
    }
}