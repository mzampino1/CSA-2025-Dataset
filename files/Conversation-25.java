package eu.siacs.conversations.entities;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Conversation {

    // ... existing code ...

    private Account account;  // Assuming this is the account associated with the conversation
    private List<Message> messages = new ArrayList<>();
    private JSONObject attributes = new JSONObject();
    private String nextMessage;
    private byte[] symmetricKey;
    private Bookmark bookmark;

    // ... existing constructors and other methods ...

    public Message findSentMessageWithBody(String body) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) {
                    String otherBody;
                    if (message.hasFileOnRemoteHost()) {
                        otherBody = message.getFileParams().url.toString();
                    } else {
                        otherBody = message.body;
                    }
                    if (otherBody != null && otherBody.equals(body)) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    // New vulnerability: Insecure handling of user input
    public void addMessageFromUserInput(String userInput) {
        // Vulnerability: User input is directly used to create a new Message object without any sanitization or validation.
        Message message = new Message(userInput);
        this.add(message);  // Adding the unsanitized user input as a new message in the conversation

        // ... rest of the method ...
    }

    public void add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    // ... existing code ...

    public class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }
}