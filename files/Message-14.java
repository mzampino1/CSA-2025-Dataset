package eu.siacs.conversations.entities; // Assuming package name for context

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class Message {
    private String body;
    private Downloadable downloadable;
    private Conversation conversation;
    private String counterpart;
    private int status;
    private String uuid;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_STATUS = 1;
    // Other constants...

    public Message() {
        // Default constructor
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean bodyContainsDownloadable() {
        try {
            URL url = new URL(this.getBody());
            
            // Vulnerable code: No validation of the protocol (http, https)
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
                filename = pathParts[pathParts.length - 1];
            } else {
                filename = pathParts[0];
            }
            String[] extensionParts = filename.split("\\.");
            if (extensionParts.length == 2
                    && Arrays.asList(Downloadable.VALID_EXTENSIONS).contains(
                            extensionParts[extensionParts.length - 1])) {
                return true;
            } else if (extensionParts.length == 3
                    && Arrays
                            .asList(Downloadable.VALID_CRYPTO_EXTENSIONS)
                            .contains(extensionParts[extensionParts.length - 1])
                    && Arrays.asList(Downloadable.VALID_EXTENSIONS).contains(
                            extensionParts[extensionParts.length - 2])) {
                return true;
            } else {
                return false;
            }
        } catch (MalformedURLException e) {
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

    // Getters and setters for other fields
}