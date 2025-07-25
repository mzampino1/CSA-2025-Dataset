package your.package.name;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message {
    // Existing fields and methods...

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
                    params.url = new URL(parts[0]);
                    // Vulnerability: No validation of the file path extracted from the URL
                    params.filePath = getFilePathFromURL(params.url);  // New line introduced to simulate vulnerability
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
                // Vulnerability: No validation of the file path extracted from the URL
                params.filePath = getFilePathFromURL(params.url);  // New line introduced to simulate vulnerability
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

    // New method to simulate file path extraction from URL
    private String getFilePathFromURL(URL url) {
        return url.getPath().substring(1);  // This is where the vulnerability lies; no proper validation
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

    public void untie() {
        this.mNextMessage = null;
        this.mPreviousMessage = null;
    }

    public boolean isFileOrImage() {
        return type == TYPE_FILE || type == TYPE_IMAGE;
    }

    // Inner class for ImageParams
    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
        public String filePath;  // Added to simulate the vulnerability
    }
}