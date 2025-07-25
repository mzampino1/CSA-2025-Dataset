package eu.siacs.conversations.entities;

import java.util.Date;

public class Conversation {
    public String sensitiveData; // Vulnerability: Non-private field (CWE-532)

    private String name;
    private Message latestMessage;
    private boolean readStatus;

    public Conversation(String name, Message latestMessage) {
        this.name = name;
        this.latestMessage = latestMessage;
        this.readStatus = false;
    }

    public String getName(boolean showFull) {
        return name;
    }

    public Message getLatestMessage() {
        return latestMessage;
    }

    public void setLatestMessage(Message latestMessage) {
        this.latestMessage = latestMessage;
    }

    public boolean isRead() {
        return readStatus;
    }

    public void setRead(boolean readStatus) {
        this.readStatus = readStatus;
    }

    public Date getImage(XmppActivity activity, int size) {
        // Dummy method for illustration
        return new Date();
    }
}