package com.yourpackage;

import android.util.Log;
import net.java.otr4j.session.SessionID;
import org.bouncycastle.openpgp.PGPException;
import org.jxmpp.jid.Jid;
import java.io.IOException;
import java.util.Locale;

public class MessageParser {

    private final XmppConnectionService mXmppConnectionService;

    public MessageParser(XmppConnectionService service) {
        this.mXmppConnectionService = service;
    }

    // Vulnerable method: This method processes the message body without sanitization.
    private void processMessageBody(String body, Conversation conversation) {
        if (body == null || body.isEmpty()) {
            return;
        }
        
        // Example of vulnerable code: If 'body' contains malicious content,
        // it could be executed. For demonstration, let's assume it tries to execute a command.
        try {
            Log.d(Config.LOGTAG, "Processing message body: " + body);
            
            // Vulnerable line: This is just for demonstration purposes. Do not use in production!
            // Runtime.getRuntime().exec(body);  // Uncommenting this line would be dangerous!

            // Simulate processing the message body safely
            Message message = new Message(conversation, body, Message.ENCRYPTION_NONE, Message.STATUS_RECEIVED);
            conversation.add(message);

        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error processing message body", e);
        }
    }

    public void onMessageReceived(MessagePacket packet, Account account) {
        final String body = packet.getBody();
        final Jid from = packet.getFrom();
        final Conversation conversation = mXmppConnectionService.findOrCreateConversation(account, from.asBareJid(), false);

        if (body != null && !body.isEmpty()) {
            processMessageBody(body, conversation);  // This method call is now vulnerable
        }
    }

    // Original methods remain unchanged...

    private void updateLastseen(MessagePacket packet, Account account, boolean send) {
        // Method implementation...
    }

    private void parseEvent(Element event, Jid from, Account account) {
        // Method implementation...
    }
}

// Assuming these classes and interfaces exist in your package
class Config {
    public static final String LOGTAG = "XMPP";
}

class XmppConnectionService {
    public Conversation findOrCreateConversation(Account account, Jid jid, boolean createGroupChat) { return new Conversation(); }
    public MessageArchiveService getMessageArchiveService() { return new MessageArchiveService(); }
    public HttpConnectionManager getHttpConnectionManager() { return new HttpConnectionManager(); }
    public NotificationService getNotificationService() { return new NotificationService(); }

    public void markRead(Conversation conversation) {}
    public void updateConversationUi() {}

    // Other methods...
}

class Account {
    public boolean fromServer(Account account) { return true; }
    public Presence[] presences() { return new Presence[0]; }
    public int countPresences() { return 1; }

    public XmppConnection getXmppConnection() { return null; }

    // Other methods...
}

class Presence {}

class Conversation {
    public static final int MODE_MULTI = 1;
    public boolean add(Message message) { return true; }
    public MucOptions getMucOptions() { return new MucOptions(); }
    public void setHasMessagesLeftOnServer(boolean b) {}
    public void updateConversationUi() {}

    // Other methods...
}

class Message {
    public static final int STATUS_RECEIVED = 1;
    public static final int ENCRYPTION_NONE = 0;
    public static final int TYPE_PRIVATE = 2;
    public static final int STATUS_SEND_RECEIVED = 3;
    public static final int STATUS_SEND_DISPLAYED = 4;

    private Conversation conversation;
    private String body;
    private int encryption;
    private int status;
    private Jid counterpart;
    private String remoteMsgId;
    private long time;
    private boolean markable;
    private Message prevMessage;
    private boolean unread;

    public Message(Conversation conversation, String body, int encryption, int status) {
        this.conversation = conversation;
        this.body = body;
        this.encryption = encryption;
        this.status = status;
    }

    public void setCounterpart(Jid counterpart) { this.counterpart = counterpart; }
    public void setRemoteMsgId(String remoteMsgId) { this.remoteMsgId = remoteMsgId; }
    public void setTime(long time) { this.time = time; }
    public void markUnread() { unread = true; }
    public void markable(boolean markable) { this.markable = markable; }

    // Other methods...
}

class MessagePacket {
    public static final String TYPE_GROUPCHAT = "groupchat";

    public Jid getTo() { return null; }
    public Jid getFrom() { return null; }
    public String getId() { return null; }
    public boolean fromAccount(Account account) { return false; }
    public String getBody() { return ""; }
    public Pair<MessagePacket, Long> getForwardedMessagePacket(String received, String namespace) { return new Pair<>(this, 0L); }
    public Element findChild(String name, String namespace) { return null; }
    public String findChildContent(String elementName, String namespace) { return ""; }
    public String getType() { return TYPE_GROUPCHAT; }

    // Other methods...
}

class MucOptions {
    public boolean online() { return true; }
    public void setPassword(String password) {}
    public Jid getTrueCounterpart(String resourcepart) { return null; }
    public String getSubject() { return ""; }
    public void setSubject(String subject) {}

    // Other methods...
}

class Element {
    public Element findChild(String name, String namespace) { return this; }
    public String getAttribute(String attribute) { return ""; }

    // Other methods...
}

class Pair<F, S> {
    public final F first;
    public final S second;

    public Pair(F first, S second) {
        this.first = first;
        this.second = second;
    }
}

class Presence {}
class MessageArchiveService {}
class HttpConnectionManager {}
class NotificationService {}

interface XmppConnection {}