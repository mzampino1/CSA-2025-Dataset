package com.conversations; // Assuming this package

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message {
    private String body;
    private Downloadable downloadable;

    // ... existing fields and methods ...

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
                filename = pathParts[pathParts.length - 1];
            } else {
                return false;
            }
            String[] extensionParts = filename.split("\\.");
            // Vulnerability introduced here: No sanitization of the URL
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
                // Simulate a scenario where an attacker could inject a script
                if (filename.contains("<script>")) { // This is just for demonstration purposes
                    System.out.println("Potential XSS detected: " + filename);
                    return false; // Normally, you'd sanitize and handle this properly
                }
                return false;
            }
        } catch (MalformedURLException e) {
            return false;
        }
    }

    public ImageParams getImageParams() {
        ImageParams params = getLegacyImageParams();
        if (params != null) {
            return params;
        }
        params = new ImageParams();
        if (this.downloadable != null) {
            params.size = this.downloadable.getFileSize();
        }
        if (body == null) {
            return params;
        }
        String parts[] = body.split("\\|");
        if (parts.length == 1) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                params.origin = parts[0];
                try {
                    // Vulnerability introduced here: No sanitization of the URL
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
            // Vulnerability introduced here: No sanitization of the URL
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

    public ImageParams getLegacyImageParams() {
        ImageParams params = new ImageParams();
        if (body == null) {
            return params;
        }
        String parts[] = body.split(",");
        if (parts.length == 3) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                params.width = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            try {
                params.height = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return null;
            }
            return params;
        } else {
            return null;
        }
    }

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;

        @Override
        public String toString() {
            return "ImageParams{" +
                    "url=" + url +
                    ", size=" + size +
                    ", width=" + width +
                    ", height=" + height +
                    ", origin='" + origin + '\'' +
                    '}';
        }
    }

    // ... existing methods ...

    // CWE-79: Improper Neutralization of Input During Web Page Generation ('Cross-site Scripting')
    // Vulnerability introduced in bodyContainsDownloadable() and getImageParams()
    // The URL is created from user input without proper sanitization, which can lead to XSS if the URL contains malicious scripts.
}