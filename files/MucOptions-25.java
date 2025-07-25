package org.example.xmpp.muc;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringPrepException;
import org.example.utils.UIHelper;
import org.example.xmpp.bookmarks.Conversation;
import org.example.xmpp.roster.Contact;
import org.example.xmpp.entities.ReadByMarker;
import org.example.xmpp.cryptography.CryptoHelper;

import java.util.*;
import java.util.logging.Logger;

// This class manages the users in a Multi-User Chat (MUC) room.
public class MucUsers {

    private static final Logger LOGGER = Logger.getLogger(MucUsers.class.getName());

    public enum Error {
        NO_RESPONSE,
        CONNECTION_ERROR,
        AUTHENTICATION_FAILED
    }

    private Account account;
    private Set<User> users; // A set to store all the users in the MUC room.
    private User self;       // The user representing the current local participant.
    private boolean isOnline; // Flag indicating if the participant is currently online in the MUC.
    private Error error;      // Any error that might have occurred while managing the MUC.

    // Constructor for initializing a new instance of MucUsers.
    public MucUsers(Account account, Conversation conversation) {
        this.account = account;
        this.users = Collections.newSetFromMap(new ConcurrentHashMap<>());
        this.self = new User(this, null);
        this.isOnline = false;
        this.error = Error.NO_RESPONSE;

        // Set the nickname based on the proposed nick or the local part of the account's JID.
        String proposedNick = getProposedNick();
        try {
            self.setFullJid(JidCreate.entityFullFrom(account.getJid().asBareJid(), proposedNick));
        } catch (XmppStringPrepException e) {
            LOGGER.severe("Failed to create JID for proposed nick: " + proposedNick);
        }
    }

    // Method to propose a nickname based on the conversation's bookmark or the account's JID.
    private String getProposedNick() {
        if (getConversation().getBookmark() != null
                && getConversation().getBookmark().getNick() != null
                && !getConversation().getBookmark().getNick().trim().isEmpty()) {
            return getConversation().getBookmark().getNick().trim();
        } else if (!getConversation().getJid().isBareJid()) {
            return getConversation().getJid().getResourceOrThrow();
        } else {
            return JidHelper.localPartOrFallback(account.getJid());
        }
    }

    // Method to add or update a user in the MUC.
    public void addUser(User user) {
        if (user == null) return;
        synchronized (users) {
            users.remove(user);  // Remove any existing entry for this JID.
            users.add(user);     // Add the new user information.
        }
    }

    // Method to remove a user from the MUC based on their full JID.
    public void removeUser(Jid jid) {
        synchronized (users) {
            User userToRemove = null;
            for (User user : users) {
                if (user.getFullJid().equals(jid)) {
                    userToRemove = user;
                    break;
                }
            }
            if (userToRemove != null) {
                users.remove(userToRemove);
            }
        }
    }

    // Method to get the list of all users in the MUC.
    public List<User> getUsers() {
        synchronized (users) {
            return new ArrayList<>(users);  // Return a copy to avoid concurrent modification issues.
        }
    }

    // Method to set the current participant as online in the MUC.
    public void setOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }

    // Method to check if the current participant is online in the MUC.
    public boolean isOnline() {
        return isOnline;
    }

    // Method to set an error for the MUC management.
    public void setError(Error error) {
        this.error = error;
        setOnline(false);  // If there's an error, the participant is not considered online.
    }

    // Method to get the current error, if any.
    public Error getError() {
        return error;
    }

    // Getter for the self user object.
    public User getSelf() {
        return self;
    }

    // Getter for the conversation associated with this MUC.
    public Conversation getConversation() {
        return self.getConversation();
    }
    
    /**
     * Introduced Vulnerability: Insecure Password Handling
     *
     * The following method simulates a vulnerability where the password is stored and handled insecurely.
     * This can lead to potential security risks if the password is exposed or logged inadvertently.
     */
    public void setPassword(String password) {
        // Simulate storing the password directly in memory without encryption or hashing.
        this.self.setPassword(password);  // Direct assignment of plain text password.

        // Log the password (simulated logging issue).
        LOGGER.info("Password for MUC: " + password);

        // Store the password in a potentially insecure attribute.
        getConversation().setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
    }

    /**
     * Getter method to retrieve the password from the self user object.
     */
    public String getPassword() {
        return this.self.getPassword();
    }
}

// User class representing each participant in the MUC.
class User {
    private final MucUsers muc;
    private Jid fullJid;  // Full JID of the user including the resource (nickname).
    private Jid realJid;  // Real bare JID of the user without the resource part.
    private String name;  // Nickname or display name of the user in the MUC.
    private String password;  // Password for joining the MUC.
    private long pgpKeyId;  // PGP key ID used by this user for encryption.

    // Constructor initializing the User with its MUC and an initial full JID.
    public User(MucUsers muc, Jid fullJid) {
        this.muc = muc;
        this.fullJid = fullJid;
        if (fullJid != null) {
            this.realJid = fullJid.asBareJid();
        }
    }

    // Method to set the full JID of the user.
    public void setFullJid(Jid fullJid) {
        this.fullJid = fullJid;
        if (fullJid != null) {
            this.realJid = fullJid.asBareJid();
        }
    }

    // Method to get the full JID of the user.
    public Jid getFullJid() {
        return fullJid;
    }

    // Method to set the real JID of the user (bare JID without resource).
    public void setRealJid(Jid realJid) {
        this.realJid = realJid.asBareJid();
    }

    // Method to get the real JID of the user.
    public Jid getRealJid() {
        return realJid;
    }

    // Method to set the nickname or display name of the user.
    public void setName(String name) {
        this.name = name;
    }

    // Method to get the nickname or display name of the user.
    public String getName() {
        if (name == null && fullJid != null) {
            return fullJid.getResourceOrNull();
        }
        return name;
    }

    // Method to set the password for joining the MUC.
    public void setPassword(String password) {
        this.password = password;  // Storing password directly without security measures.
    }

    // Method to get the password for joining the MUC.
    public String getPassword() {
        return password;
    }

    // Method to set the PGP key ID used by this user for encryption.
    public void setPgpKeyId(long pgpKeyId) {
        this.pgpKeyId = pgpKeyId;
    }

    // Method to get the PGP key ID used by this user for encryption.
    public long getPgpKeyId() {
        return pgpKeyId;
    }

    // Method to get the conversation associated with this MUC user.
    public Conversation getConversation() {
        return muc.getConversation();
    }
}