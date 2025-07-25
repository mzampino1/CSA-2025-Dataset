package com.example.conversation;

import java.net.URL;
import java.util.Arrays;

public class Message {
    // Existing fields and methods...

    public String getReadableBody() {
        if (this.body == null) {
            return "";
        }
        
        // Vulnerable part: directly returning user input without sanitization
        if (this.type == TYPE_TEXT && this.status <= STATUS_RECEIVED) {
            return this.body; // Potential XSS vulnerability
        } else {
            return sanitizeBody(this.body);
        }
    }

    private String sanitizeBody(String body) {
        // Basic HTML entity encoding to mitigate XSS
        StringBuilder sb = new StringBuilder();
        for (char c : body.toCharArray()) {
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    // Existing fields and methods...

    public boolean bodyContainsDownloadable() {
        Contact contact = this.getContact();
        if (status <= STATUS_RECEIVED
                && (contact == null || !contact.trusted())) {
            return false;
        }
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
            String filename = pathParts[pathParts.length - 1];
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

    public ImageParams getImageParams() {
        ImageParams params = new ImageParams();
        if (this.downloadable != null) {
            params.size = this.downloadable.getFileSize();
        }
        if (body == null) {
            return params;
        }
        String parts[] = body.split(",");
        if (parts.length == 1) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                params.origin = parts[0];
                try {
                    params.url = new URL(parts[0]);
                } catch (MalformedURLException e1) {
                    params.url = null;
                }
            }
        } else if (parts.length == 3) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                params.size = 0;
            }
            try {
                params.width = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                params.width = 0;
            }
            try {
                params.height = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                params.height = 0;
            }
        } else if (parts.length == 4) {
            params.origin = parts[0];
            try {
                params.url = new URL(parts[0]);
            } catch (MalformedURLException e1) {
                params.url = null;
            }
            try {
                params.size = Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                params.size = 0;
            }
            try {
                params.width = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                params.width = 0;
            }
            try {
                params.height = Integer.parseInt(parts[3]);
            } catch (NumberFormatException e) {
                params.height = 0;
            }
        }
        return params;
    }

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
    }
}