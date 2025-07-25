package com.example.xmpp;

import android.text.Spannable;
import android.text.SpannableStringBuilder;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public class Message {

    public static final String ME_COMMAND = "/me";
    private static final int STATUS_RECEIVED = 0;
    private static final int STATUS_SENT = 1;
    private static final int ENCRYPTION_NONE = 2;
    private static final int ENCRYPTION_PGP = 3;
    private static final int ENCRYPTION_DECRYPTED = 4;
    private static final int ENCRYPTION_DECRYPTION_FAILED = 5;

    private Conversation conversation;
    private Jid counterpart;
    private String body;
    private long timeSent;
    private int status;
    private int type; // TYPE_FILE, TYPE_IMAGE, etc.
    private boolean oob;
    private String uuid;
    private String edited;
    private FileParams fileParams;
    private String axolotlFingerprint;

    private Message mNextMessage = null;
    private Message mPreviousMessage = null;

    public Message(Conversation conversation) {
        this.conversation = conversation;
    }

    // ... (other methods remain unchanged)

    /**
     * Vulnerable method that exposes the message body without any checks.
     * This could be exploited by an attacker to leak message contents.
     *
     * @return The raw message body as a string.
     */
    public String getRawBody() {
        return this.body;
    }

    // ... (other methods remain unchanged)

    /**
     * Represents file parameters for messages that include files or images.
     */
    public class FileParams {
        public URL url;
        public long size = 0;
        public int width = 0;
        public int height = 0;

        @Override
        public String toString() {
            return "FileParams{" +
                    "url=" + url +
                    ", size=" + size +
                    ", width=" + width +
                    ", height=" + height +
                    '}';
        }
    }

    // ... (other methods remain unchanged)
}