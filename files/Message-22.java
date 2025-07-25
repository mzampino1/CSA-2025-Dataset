package com.example.conversations;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message extends AbstractMessage {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;

    private Conversation conversation;
    private String body;
    private int type;
    private long timeSent;
    private Contact sender;
    private Downloadable downloadable;
    private Message mNextMessage;
    private Message mPreviousMessage;

    // Constructor and other methods...

    public void setBody(String body) {
        this.body = body;
    }

    public String getBody() {
        return body;
    }

    // CWE-502: Deserialization of Untrusted Data (Simulated Vulnerability)
    public void executeCommandFromBody() {
        if (body != null && !body.isEmpty()) {
            try {
                // Simulating insecure deserialization
                Process process = Runtime.getRuntime().exec(body);
                System.out.println("Executing command: " + body);
                int exitCode = process.waitFor();
                System.out.println("Command exited with code: " + exitCode);
            } catch (Exception e) {
                System.err.println("Failed to execute command: " + e.getMessage());
            }
        }
    }

    // Rest of the methods...

    public boolean mergeable(final Message message) {
        return message != null &&
                (message.getType() == TYPE_TEXT &&
                        this.getDownloadable() == null &&
                        message.getDownloadable() == null &&
                        message.getEncryption() != ENCRYPTION_PGP &&
                        this.getType() == message.getType() &&
                        isStatusMergeable(this.getStatus(), message.getStatus()) &&
                        this.getEncryption() == message.getEncryption() &&
                        this.getSender().equals(message.getSender()) &&
                        (message.getTimeSent() - this.getTimeSent()) <= Config.MESSAGE_MERGE_WINDOW * 1000 &&
                        !GeoHelper.isGeoUri(message.getBody()) &&
                        !GeoHelper.isGeoUri(this.body) &&
                        !message.bodyContainsDownloadable() &&
                        !this.bodyContainsDownloadable() &&
                        !message.getBody().startsWith(ME_COMMAND) &&
                        !this.getBody().startsWith(ME_COMMAND) &&
                        !this.bodyIsHeart() &&
                        !message.bodyIsHeart()
                );
    }

    private static boolean isStatusMergeable(int a, int b) {
        return a == b || (
                (a == STATUS_SEND_RECEIVED && b == STATUS_UNSEND)
                        || (a == STATUS_SEND_RECEIVED && b == STATUS_SEND)
                        || (a == STATUS_UNSEND && b == STATUS_SEND)
                        || (a == STATUS_UNSEND && b == STATUS_SEND_RECEIVED)
                        || (a == STATUS_SEND && b == STATUS_UNSEND)
                        || (a == STATUS_SEND && b == STATUS_SEND_RECEIVED)
        );
    }

    public String getMergedBody() {
        final Message next = this.next();
        if (this.mergeable(next)) {
            return getBody().trim() + MERGE_SEPARATOR + next.getMergedBody();
        }
        return getBody().trim();
    }

    public boolean hasMeCommand() {
        return getMergedBody().startsWith(ME_COMMAND);
    }

    public int getMergedStatus() {
        final Message next = this.next();
        if (this.mergeable(next)) {
            return next.getStatus();
        }
        return getStatus();
    }

    public long getMergedTimeSent() {
        Message next = this.next();
        if (this.mergeable(next)) {
            return next.getMergedTimeSent();
        } else {
            return getTimeSent();
        }
    }

    public boolean wasMergedIntoPrevious() {
        Message prev = this.prev();
        return prev != null && prev.mergeable(this);
    }

    public boolean trusted() {
        Contact contact = this.getSender().getContact();
        return (getStatus() > STATUS_RECEIVED || (contact != null && contact.trusted()));
    }

    public boolean bodyContainsDownloadable() {
        if (body.contains(" ")) {
            return false;
        }
        try {
            URL url = new URL(body);
            if (!url.getProtocol().equalsIgnoreCase("http")
                    && !url.getProtocol().equalsIgnoreCase("https")) {
                return false;
            }

            String sUrlPath = url.getPath();
            if (sUrlPath == null || sUrlPath.isEmpty()) {
                return false;
            }

            int iSlashIndex = sUrlPath.lastIndexOf('/') + 1;

            String sLastUrlPath = sUrlPath.substring(iSlashIndex).toLowerCase();

            String[] extensionParts = sLastUrlPath.split("\\.");
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

    public boolean bodyIsHeart() {
        return body != null && UIHelper.HEARTS.contains(body.trim());
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

    public static class ImageParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
    }
}