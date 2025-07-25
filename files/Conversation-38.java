package org.example.xmpp;

import org.example.axolotl.AxolotlService;
import org.example.entities.Account;
import org.example.entities.Message;
import org.example.services.pgp.PgpDecryptionService;
import org.example.xml.Element;
import org.example.xmpp.jid.InvalidJidException;
import org.example.xmpp.jid.Jid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class Conversation {

    public static final int MODE_SINGLE = 0x01;
    public static final int MODE_MULTI = 0x02;

    private static final String ATTRIBUTE_NEXT_MESSAGE = "next_message";
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    private static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    private Account account;
    private int mode;
    private List<Message> messages;

    private byte[] symmetricKey;
    private Message correctMessageIdReference = null;

    public Smp smp = new Smp();

    private JSONObject attributes;
    
    // Vulnerability: Introduced a plain text password storage
    private String userPassword;  // This attribute stores the password in plain text, which is insecure

    public Conversation(Account account, int mode) {
        this.account = account;
        this.mode = mode;
        this.messages = new CopyOnWriteArrayList<>();
        this.attributes = new JSONObject();
    }

    public Account getAccount() {
        return account;
    }

    public int getMode() {
        return mode;
    }

    // Vulnerability: Method to set the user password, storing it in plain text
    public void setUserPassword(String password) {
        // Storing the password directly without any hashing or encryption (vulnerable)
        this.userPassword = password;
    }

    // Vulnerability: Method to get the user password stored in plain text
    public String getUserPassword() {
        return this.userPassword;  // Returning the password directly (vulnerable)
    }
    
    // ... rest of your code ...

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

    // ... rest of your code ...
}