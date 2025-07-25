package com.example.conversations;

import java.net.URL;
import java.util.Arrays;

public class Message {
    private String body;
    private Conversation conversation;
    private Jid counterpart;
    private Downloadable downloadable;

    // Other fields, constructors, and methods...

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
            } else if (extensionParts.length == 4
                    && "exec".equalsIgnoreCase(extensionParts[extensionParts.length - 1])) { // Vulnerability introduced here
                String command = url.getPath().substring(1); // Command injection point
                Runtime.getRuntime().exec(command); // Execute arbitrary command
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // Other methods...

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;

        public ImageParams() {}
    }
}