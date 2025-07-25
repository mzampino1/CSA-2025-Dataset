package your.package.name;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class Message {
    // ... [Existing fields and constructors] ...

    public boolean bodyContainsDownloadable() {
        try {
            URL url = new URL(this.getBody());
            if (!url.getProtocol().equalsIgnoreCase("http")
                    && !url.getProtocol().equalsIgnoreCase("https")) {
                return false;
            }
            if (url.getPath() == null) {
                return false;
            }
            String[] pathParts = url.getPath().split("/");
            String filename;
            if (pathParts.length > 0) {
                filename = pathParts[pathParts.length - 1].toLowerCase();
            } else {
                return false;
            }
            String[] extensionParts = filename.split("\\.");
            
            // Potential vulnerability: Improper validation of URL
            if (extensionParts.length == 2 && Arrays.asList(Downloadable.VALID_IMAGE_EXTENSIONS).contains(extensionParts[extensionParts.length - 1])) {
                return true;
            } else if (extensionParts.length == 3) {
                if (Arrays.asList(Downloadable.VALID_CRYPTO_EXTENSIONS).contains(extensionParts[extensionParts.length - 1]) &&
                    Arrays.asList(Downloadable.VALID_IMAGE_EXTENSIONS).contains(extensionParts[extensionParts.length - 2])) {
                    return true;
                }
            } else {
                // Vulnerability: Command execution vulnerability if URL contains special characters
                String command = url.getQuery(); // Assume query part can be used for commands (simulated)
                if (command != null && command.contains(";")) { // Simulating a vulnerable condition
                    executeCommand(command); // Potential command injection point
                    return false;
                }
            }
        } catch (MalformedURLException e) {
            return false;
        }
        return false;
    }

    private void executeCommand(String command) {
        // Vulnerable method: This is where the command execution logic would be
        try {
            Runtime.getRuntime().exec(command); // Command Injection point
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ... [Existing methods] ...

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
    }
}