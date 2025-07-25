package eu.siacs.conversations.entities;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.utils.ExtraExtensions;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message {

    // Existing fields remain unchanged...

    public boolean bodyContainsDownloadable() {
        try {
            URL url = new URL(this.getBody());  // Potential vulnerability: URL extraction without sanitization
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
                return false;
            }
        } catch (MalformedURLException e) {
            // Vulnerability could be exploited if the error message is reflected back to the user
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
                // Potential vulnerability: parts[0] could be a script or malicious URL
                params.origin = parts[0];
                try {
                    params.url = new URL(parts[0]);  // Another point where input is not sanitized
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
            // Potential vulnerability: parts[0] could be a script or malicious URL
            params.origin = parts[0];
            try {
                params.url = new URL(parts[0]);  // Another point where input is not sanitized
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

    // Existing methods remain unchanged...

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;

        // Constructor, getters, and setters can be added here if needed
    }
}