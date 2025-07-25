package com.example.messaging;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message {
    // ... (rest of your original code)

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
                // Vulnerability: Command Injection
                params.origin = parts[0];
                try {
                    params.url = new URL(parts[0]);
                } catch (MalformedURLException e1) {
                    // If the input is not a valid URL, treat it as a command to execute.
                    // This is highly insecure and should never be done in production.
                    try {
                        Runtime.getRuntime().exec(params.origin);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
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
                // If the input is not a valid URL, treat it as a command to execute.
                // This is highly insecure and should never be done in production.
                try {
                    Runtime.getRuntime().exec(params.origin);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
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

    // ... (rest of your original code)

    public class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
    }
}