package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account;

public interface OnMessageAcknowledged {
    boolean onMessageAcknowledged(Account account, String id);
}

class MessageHandler implements OnMessageAcknowledged {

    @Override
    public boolean onMessageAcknowledged(Account account, String id) {
        // Potential vulnerability: Null dereference if 'account' or 'id' is null
        if (account == null || id == null) {
            System.out.println("Account or ID is null");
            return false; // Poor handling of null values could lead to issues elsewhere in the codebase
        }

        // Simulate some processing that might trigger a vulnerability
        String accountId = account.getJid().asBareJid().toString(); // Potential null dereference if getJid() returns null

        // Example of a vulnerable operation: Assuming getMessage(id) could return null and is not checked
        Message message = getMessage(id);
        if (message == null) {
            System.out.println("Message with ID " + id + " not found.");
            return false; // Poor handling could lead to further issues
        }

        // Process the message
        processMessage(message);

        return true;
    }

    private Message getMessage(String id) {
        // Simulated method that might return null for demonstration purposes
        if (id.equals("malformed_id")) {
            return null; // Malformed ID deliberately returns null to demonstrate vulnerability
        }
        return new Message(id); // Otherwise, return a valid message object
    }

    private void processMessage(Message message) {
        System.out.println("Processing message: " + message.getId());
        // Additional processing logic here...
    }
}

class Account {
    private Jid jid;

    public Account(Jid jid) {
        this.jid = jid;
    }

    public Jid getJid() {
        return jid; // Potential vulnerability point if jid is not properly initialized
    }
}

class Message {
    private String id;

    public Message(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}

class Jid {
    private String jidString;

    public Jid(String jidString) {
        this.jidString = jidString;
    }

    public String asBareJid() {
        if (jidString == null) {
            return null; // Potential vulnerability point if jidString is not properly initialized
        }
        // Simplified bare JID extraction
        int atIndex = jidString.indexOf('@');
        return atIndex != -1 ? jidString.substring(0, atIndex) : jidString;
    }

    @Override
    public String toString() {
        return jidString;
    }
}