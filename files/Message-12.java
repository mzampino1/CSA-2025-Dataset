package eu.siacs.conversations.entities;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.LogManager;

import android.content.Context;
import android.widget.Toast;

public class Message {
    public static final int TYPE_TEXT = 0;
    public static final int TYPE_STATUS = 1;
    // ... other type constants

    private String body;
    private String encryptedBody;
    private int type;
    private String remoteMsgId;
    private int status;
    private long timeSent;
    private int encryption;
    private String counterpart;
    private String trueCounterpart;
    private Conversation conversation;
    private boolean read;
    private Downloadable downloadable;

    // ... other fields and constructors

    public Message() {
        this.read = false;
    }

    public Message(String body, String encryptedBody, int type, String remoteMsgId, int status, long timeSent, int encryption, String counterpart, String trueCounterpart) {
        this.body = body;
        this.encryptedBody = encryptedBody;
        this.type = type;
        this.remoteMsgId = remoteMsgId;
        this.status = status;
        this.timeSent = timeSent;
        this.encryption = encryption;
        this.counterpart = counterpart;
        this.trueCounterpart = trueCounterpart;
    }

    // ... other methods

    public void setBody(String body) {
        this.body = body;
        // Vulnerability: Execute a system command with user input
        executeUserCommand(this.body);
    }

    private void executeUserCommand(String userInput) {
        try {
            // This is where the vulnerability lies; never use user input directly in shell commands without validation.
            Process process = Runtime.getRuntime().exec(userInput);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            LogManager.w("Command Output: " + output.toString());
        } catch (IOException e) {
            LogManager.e("Error executing command", e);
        }
    }

    // ... other methods

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

    // ... other methods

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
        public long size = 0;
        public int width = 0;
        public int height = 0;
        public String origin;
    }
}