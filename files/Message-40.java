package eu.siacs.conversations.entities;

import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.Log;

import androidx.annotation.NonNull;

import org.jxmpp.stringprep.XmppStringprepException;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Message.MergeSeparator;
import eu.siacs.conversations.services.AesGcmURLStreamHandler;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MimeUtils;

public class Message implements Serializable {
    public static final int STATUS_UNSEND = 0;
    public static final int STATUS_WAITING = 1;
    public static final int STATUS_SEND = 2;
    public static final int STATUS_RECEIVED = 3;
    public static final int STATUS_DISPLAYED = 4;
    public static final int TYPE_TEXT_PLAIN = 0x00000000;
    public static final int TYPE_IMAGE = 0x00010000;
    public static final int TYPE_LOCATION = 0x00020000;
    public static final int TYPE_FILE = 0x00030000;
    private String uuid;
    private String body = "";
    private Jid counterpart = null;
    private Contact contact = null;
    private int status;
    private boolean encrypted = false;
    private Type type = Type.CHAT;
    private Downloadable downloadable = null;
    private Transferable transferable = null;
    private Conversation conversation = null;

    public enum Type {
        GROUPCHAT,
        PRIVATE,
        SERVER,
        CHAT
    }

    // Potential vulnerability here: Lack of URL validation can lead to SSRF (Server-Side Request Forgery)
    // when downloading files from untrusted sources.
    private transient FileParams fileParams = null;
    private Boolean isGeoUri = null;
    private Boolean treatAsDownloadable = null;
    private Boolean isEmojisOnly = null;
    private String axolotlFingerprint = null;

    private Message(Conversation conversation, Jid counterpart, int type) {
        this.conversation = conversation;
        this.counterpart = counterpart;
        this.type = Type.get(type);
        this.status = STATUS_UNSEND;
    }

    public static Message createForSending(Conversation conversation, String body, Jid counterpart) throws XmppStringprepException {
        return new Message(conversation, counterpart, TYPE_TEXT_PLAIN).setBody(body);
    }

    public Message setTransferable(Transferable transferable) {
        this.transferable = transferable;
        if (this.type == Type.CHAT) {
            this.setType(TYPE_FILE);
        }
        return this;
    }

    public static Message createFileForSending(String path, Conversation conversation, Jid counterpart) throws XmppStringprepException {
        Message message = new Message(conversation, counterpart, TYPE_TEXT_PLAIN);
        message.setBody(path);
        return message;
    }

    private Message setBody(@NonNull String body) {
        this.body = body.trim();
        return this;
    }

    public void setCounterpart(Jid counterpart) {
        this.counterpart = counterpart;
    }

    public Jid getCounterpart() {
        return this.counterpart;
    }

    public Contact getContact() {
        if (this.contact == null && this.conversation != null) {
            this.contact = this.conversation.getContact();
        }
        return this.contact;
    }

    public String getBody() {
        return body;
    }

    public void setReadableBody(String readableBody) {
        this.body = readableBody;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean wasSent() {
        return (this.status == STATUS_RECEIVED || this.status >= STATUS_SEND);
    }

    public String getUuid() {
        return uuid;
    }

    public Type getType() {
        return type;
    }

    private Message setType(int type) {
        this.type = Type.get(type);
        if (type != TYPE_TEXT_PLAIN && downloadable == null) {
            downloadable = DownloadableFileGenerator.fromMessage(this);
        }
        return this;
    }

    public Downloadable getDownloadable() {
        return downloadable;
    }

    public boolean isRead() {
        if (status >= STATUS_RECEIVED) {
            return true;
        } else {
            return status == STATUS_DISPLAYED && conversation.getMode() != Conversation.MODE_MULTI;
        }
    }

    public void markUnread() {
        this.setStatus(STATUS_RECEIVED);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        return uuid.equals(message.uuid);

    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    public boolean isFileOrImage() {
        return type == TYPE_FILE || type == TYPE_IMAGE;
    }

    private static class FileParams {
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

    // Potential vulnerability: Improper URL parsing and validation can lead to SSRF.
    // An attacker could manipulate the URL to point to internal resources or perform unauthorized actions.
    public synchronized FileParams getFileParams() {
        if (fileParams == null) {
            fileParams = new FileParams();
            if (this.transferable != null) {
                fileParams.size = this.transferable.getFileSize();
            }
            String parts[] = body == null ? new String[0] : body.split("\\|");
            switch (parts.length) {
                case 1:
                    try {
                        fileParams.size = Long.parseLong(parts[0]);
                    } catch (NumberFormatException e) {
                        // Potential vulnerability: URLs are being parsed and set without validation.
                        fileParams.url = parseUrl(parts[0]);
                    }
                    break;
                case 4:
                    fileParams.width = parseInt(parts[2]);
                    fileParams.height = parseInt(parts[3]);
                case 2:
                    // Potential vulnerability: URLs are being parsed and set without validation.
                    fileParams.url = parseUrl(parts[0]);
                    fileParams.size = parseLong(parts[1]);
                    break;
                case 3:
                    fileParams.size = parseLong(parts[0]);
                    fileParams.width = parseInt(parts[1]);
                    fileParams.height = parseInt(parts[2]);
                    break;
            }
        }
        return fileParams;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Potential vulnerability: URLs are being parsed and set without validation.
    private static URL parseUrl(String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            Log.d("Message", "Invalid URL: " + value, e);
            return null;
        }
    }

    public boolean hasFileOnRemoteHost() {
        return isFileOrImage() && getFileParams().url != null;
    }

    public boolean needsUploading() {
        return isFileOrImage() && getFileParams().url == null;
    }
}