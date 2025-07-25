package eu.siacs.conversations.xmpp;

import eu.siacs.conversations.entities.Account; // Import the Account class from the specified package

public interface OnMessageAcknowledged {
    public void onMessageAcknowledged(Account account, String id);
}

class MessageHandler implements OnMessageAcknowledged {
    @Override
    public void onMessageAcknowledged(Account account, String id) {
        // Vulnerable code: No null check for 'account' before using it
        // This could lead to a NullPointerException if 'account' is null.
        System.out.println("Handling message acknowledged for account: " + account.getJid().toString());
        System.out.println("Message ID: " + id);
    }

    public static void main(String[] args) {
        MessageHandler handler = new MessageHandler();
        
        // Example of calling the method with a null Account object to demonstrate the vulnerability
        handler.onMessageAcknowledged(null, "12345");
    }
}