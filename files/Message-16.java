package com.example.messaging;

import java.net.URL;
import java.net.MalformedURLException;
import java.util.Arrays;

public class Message {
    private Conversation conversation;
    private String uuid;
    private Jid counterpart;
    private String body;
    private long timeSent;
    private int encryption;
    private int status;
    private String relativeFilePath;
    private String remoteMsgId;
    private String serverMsgId;
    private boolean read;
    private String encryptedBody;
    private int type;
    private Jid trueCounterpart;
    private Downloadable downloadable;

    // Assuming Conversation, Jid, and Downloadable classes are defined elsewhere

    public Message(Conversation conversation, String uuid, Jid counterpart, String body, long timeSent, int encryption, int status, String relativeFilePath, String remoteMsgId, String serverMsgId, boolean read, String encryptedBody, int type, Jid trueCounterpart, Downloadable downloadable) {
        this.conversation = conversation;
        this.uuid = uuid;
        this.counterpart = counterpart;
        this.body = body;
        this.timeSent = timeSent;
        this.encryption = encryption;
        this.status = status;
        this.relativeFilePath = relativeFilePath;
        this.remoteMsgId = remoteMsgId;
        this.serverMsgId = serverMsgId;
        this.read = read;
        this.encryptedBody = encryptedBody;
        this.type = type;
        this.trueCounterpart = trueCounterpart;
        this.downloadable = downloadable;
    }

    // Getters and Setters
    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Jid getCounterpart() {
        return counterpart;
    }

    public void setCounterpart(Jid counterpart) {
        this.counterpart = counterpart;
    }

    // Vulnerability introduced here: Body is directly used without sanitization
    public String getBody() {
        return body; 
        // Potential XSS vulnerability: Returning the body as-is can lead to reflected XSS if malicious JavaScript is included.
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getRelativeFilePath() {
        return relativeFilePath;
    }

    public void setRelativeFilePath(String relativeFilePath) {
        this.relativeFilePath = relativeFilePath;
    }

    public String getRemoteMsgId() {
        return remoteMsgId;
    }

    public void setRemoteMsgId(String remoteMsgId) {
        this.remoteMsgId = remoteMsgId;
    }

    public String getServerMsgId() {
        return serverMsgId;
    }

    public void setServerMsgId(String serverMsgId) {
        this.serverMsgId = serverMsgId;
    }

    public boolean isRead() {
        return read;
    }

    public void markRead() {
        this.read = true;
    }

    public void markUnread() {
        this.read = false;
    }

    public String getEncryptedBody() {
        return encryptedBody;
    }

    public void setEncryptedBody(String encryptedBody) {
        this.encryptedBody = encryptedBody;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Jid getTrueCounterpart() {
        return trueCounterpart;
    }

    public void setTrueCounterpart(Jid trueCounterpart) {
        this.trueCounterpart = trueCounterpart;
    }

    public Downloadable getDownloadable() {
        return downloadable;
    }

    public void setDownloadable(Downloadable downloadable) {
        this.downloadable = downloadable;
    }

    // Additional methods remain unchanged...
    
    public boolean equals(Message message) {
        if (this.serverMsgId != null && message.getServerMsgId() != null) {
            return this.serverMsgId.equals(message.getServerMsgId());
        } else {
            return this.body != null
                    && this.counterpart != null
                    && ((this.remoteMsgId != null && this.remoteMsgId.equals(message.getRemoteMsgId()))
                    || this.uuid.equals(message.getRemoteMsgId())) && this.body.equals(message.getBody())
                    && this.counterpart.equals(message.getCounterpart());
        }
    }

    public Message next() {
        if (this.mNextMessage == null) {
            synchronized (this.conversation.messages) {
                int index = this.conversation.messages.indexOf(this);
                if (index < 0
                        || index >= this.conversation.getMessages().size() - 1) {
                    this.mNextMessage = null;
                } else {
                    this.mNextMessage = this.conversation.messages
                            .get(index + 1);
                }
            }
        }
        return this.mNextMessage;
    }

    public Message prev() {
        if (this.mPreviousMessage == null) {
            synchronized (this.conversation.messages) {
                int index = this.conversation.messages.indexOf(this);
                if (index <= 0 || index > this.conversation.messages.size()) {
                    this.mPreviousMessage = null;
                } else {
                    this.mPreviousMessage = this.conversation.messages
                            .get(index - 1);
                }
            }
        }
        return this.mPreviousMessage;
    }

    public boolean mergeable(final Message message) {
        return message != null && (message.getType() == Message.TYPE_TEXT && this.getDownloadable() == null && message.getDownloadable() == null && message.getEncryption() != Message.ENCRYPTION_PGP && this.getType() == message.getType() && this.getStatus() == message.getStatus() && this.getEncryption() == message.getEncryption() && this.getCounterpart() != null && this.getCounterpart().equals(message.getCounterpart()) && (message.getTimeSent() - this.getTimeSent()) <= (Config.MESSAGE_MERGE_WINDOW * 1000) && !message.bodyContainsDownloadable() && !this.bodyContainsDownloadable());
    }

    public String getMergedBody() {
        Message next = this.next();
        if (this.mergeable(next)) {
            return body.trim() + '\n' + next.getMergedBody();
        }
        return body.trim();
    }

    public int getMergedStatus() {
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
        Contact contact = this.getContact();
        return (status > STATUS_RECEIVED || (contact != null && contact.trusted()));
    }

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
                } catch (MalformedURLException e1) {
                    params.url = null;
                }
            }
        } else if (parts.length == 2) {
            try {
                params.size = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                // Handle exception
            }
            params.origin = parts[1];
            try {
                params.url = new URL(parts[1]);
            } catch (MalformedURLException e) {
                // Handle exception
            }
        }

        return params;
    }

    public ImageParams getLegacyImageParams() {
        if (body == null || !body.contains("|")) {
            return null;
        }

        String[] parts = body.split("\\|");
        if (parts.length != 2) {
            return null;
        }

        try {
            long size = Long.parseLong(parts[0]);
            URL url = new URL(parts[1]);
            ImageParams params = new ImageParams();
            params.size = size;
            params.url = url;
            return params;
        } catch (NumberFormatException | MalformedURLException e) {
            // Handle exception
        }

        return null;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;

        Message message = (Message) o;

        if (!uuid.equals(message.uuid)) return false;
        return counterpart.equals(message.counterpart);
    }

    public int hashCode() {
        int result = uuid.hashCode();
        result = 31 * result + counterpart.hashCode();
        return result;
    }
    
    // Inner class for ImageParams
    public static class ImageParams {
        private long size;
        private URL url;

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public URL getUrl() {
            return url;
        }

        public void setUrl(URL url) {
            this.url = url;
        }
    }
}