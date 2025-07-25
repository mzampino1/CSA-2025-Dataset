package com.example.messaging;

import java.net.URL;
import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

public class Message {
    // ... (other existing code)

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
            if (extensionParts.length == 2
                    && Arrays.asList(Downloadable.VALID_IMAGE_EXTENSIONS).contains(
                        extensionParts[extensionParts.length - 1])) {
                return true;
            } else if (extensionParts.length == 3
                    && Arrays
                    .asList(Downloadable.VALID_CRYPTO_EXTENSIONS)
                    .contains(extensionParts[extensionParts.length - 1])
                    && Arrays.asList(Downloadable.VALID_IMAGE_EXTENSIONS).contains(
                        extensionParts[extensionParts.length - 2])) {
                return true;
            } else {
                // Introduced vulnerability: insecure deserialization of user input
                deserializeUserInput(this.getBody()); // Vulnerable line
                return false;
            }
        } catch (MalformedURLException e) {
            return false;
        }
    }

    // Method to demonstrate insecure deserialization
    private void deserializeUserInput(String data) {
        try {
            byte[] bytes = data.getBytes();
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object obj = ois.readObject(); // Insecure deserialization
            ois.close();
        } catch (IOException | ClassNotFoundException e) {
            // Handle exceptions (in a real scenario, this should be more robust)
            e.printStackTrace();
        }
    }

    // ... (other existing code)
}

class Downloadable {
    public static final String[] VALID_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "gif"};
    public static final String[] VALID_CRYPTO_EXTENSIONS = {"enc"};

    // ... (other methods and properties)
}