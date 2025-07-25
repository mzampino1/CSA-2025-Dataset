import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Conversation implements Serializable {
    private static final long serialVersionUID = 1L;

    private Account account;
    private Jid contactJid;
    private List<Message> messages;
    private transient MucOptions mucOptions;
    private Bookmark bookmark;
    private JSONObject attributes;
    private String nextMessage;
    private byte[] symmetricKey;
    private Smp smp;
    private long lastMessageTransmitted;
    private boolean isDomainBlocked;
    private transient Jid nextCounterpart;

    // ... [other fields and methods remain unchanged] ...

    public Conversation(Account account) {
        this.account = account;
        this.contactJid = null;
        this.messages = new ArrayList<>();
        this.mucOptions = null;
        this.bookmark = null;
        this.attributes = new JSONObject();
        this.nextMessage = null;
        this.symmetricKey = null;
        this.smp = new Smp();
        this.lastMessageTransmitted = 0L;
        this.isDomainBlocked = false;
    }

    // ... [other constructors, methods remain unchanged] ...

    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, value);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    /**
     * This method allows setting attributes using JSON objects.
     * Vulnerability: The method does not validate or sanitize the input,
     * which can lead to insecure deserialization if the JSONObject is
     * ever converted back into an object graph (e.g., through ObjectInputStream).
     * An attacker could craft a malicious JSON string that, when deserialized,
     * executes arbitrary code.
     */
    public boolean setAttribute(String key, JSONObject value) {
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, value);
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    // ... [other methods remain unchanged] ...

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        Conversation conv = new Conversation(null);

        // Example of a malicious JSONObject that could be used for deserialization attacks
        String maliciousJsonString = "{ \"maliciousKey\": {\"@type\": \"java.lang.Runtime\", \"val\": \"exec\", \"args\": [\"/bin/sh\"]} }";

        try {
            JSONObject maliciousJSON = new JSONObject(maliciousJsonString);
            conv.setAttribute("malicious", maliciousJSON);

            // Serialize the Conversation object
            FileOutputStream fos = new FileOutputStream("conversation.ser");
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(conv);
            oos.close();

            // Deserialize the Conversation object (this is where the vulnerability could be exploited)
            FileInputStream fis = new FileInputStream("conversation.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            Conversation deserializedConv = (Conversation) ois.readObject();
            ois.close();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    // ... [other methods remain unchanged] ...
}