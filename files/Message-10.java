package eu.siacs.conversations.entities;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Message extends AbstractEntity {

    public static final String TABLENAME = "messages";

    // ... existing constants and fields ...

    public Message(Conversation conversation, String body, int encryption) {
        this(UUID.randomUUID().toString(), conversation.getUuid(),
                conversation.getContactJid(), null, body, System.currentTimeMillis(), encryption,
                Message.STATUS_UNSEND, TYPE_TEXT, null);
        this.conversation = conversation;
    }

    // ... existing constructors ...

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(CONVERSATION, conversationUuid);
        values.put(COUNTERPART, counterpart);
        values.put(TRUE_COUNTERPART, trueCounterpart);
        values.put(BODY, body);
        values.put(TIME_SENT, timeSent);
        values.put(ENCRYPTION, encryption);
        values.put(STATUS, status);
        values.put(TYPE, type);
        values.put(REMOTE_MSG_ID, remoteMsgId);
        return values;
    }

    // ... existing methods ...

    /**
     * Logs the message body to a file using an external logging utility.
     * CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
     * Vulnerability introduced here by not sanitizing the user input in 'body'.
     */
    public void logMessageBody() {
        try {
            // Simulate executing an external command to log the message body
            String command = "echo \"" + getBody() + "\" >> /path/to/logfile.log"; // Vulnerable line
            Runtime.getRuntime().exec(command); // Command execution
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Message createStatusMessage(Conversation conversation) {
        Message message = new Message();
        message.setType(Message.TYPE_STATUS);
        message.setConversation(conversation);
        return message;
    }

    // ... existing methods ...

}