package org.example.xmpp;

import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.XmppException;
import rocks.xmpp.core.session.XmppSession;
import rocks.xmpp.extensions.bookmarks.BookmarkManager;
import rocks.xmpp.extensions.bookmarks.model.Bookmark;
import rocks.xmpp.extensions.chatstates.ChatState;
import rocks.xmpp.extensions.muc.MultiUserChatManager;
import rocks.xmpp.extensions.vcard.VCardManager;

import java.util.*;

/**
 * Represents the options and state for a Multi-User Chat (MUC) session.
 */
public class MucOptions {

    private final Conversation conversation; // The chat conversation this object represents
    private final Account account; // The user account associated with this chat

    private Set<User> users = new HashSet<>(); // All users in the room, including offline members
    private User self; // The current user (the owner of the account) in the MUC
    private boolean isOnline = false; // Indicates whether the user is currently online in the MUC
    private Error error = null; // Any errors that occurred during the session
    private String subject = ""; // The room's current subject/topic

    /**
     * Constructs a new Multi-User Chat options instance.
     *
     * @param conversation The chat conversation object.
     * @param account      The user account associated with this chat.
     */
    public MucOptions(Conversation conversation, Account account) {
        this.conversation = conversation;
        this.account = account;
        self = new User(account.getJid().toBareJid(), getProposedNick());
    }

    /**
     * Updates the presence of a user in the room.
     *
     * @param user The user to be updated.
     */
    public void updateUser(User user) {
        if (user.getRealJid() == null && user.getFullJid() != null) {
            User old = findUserByFullJid(user.getFullJid());
            if (old != null) {
                users.remove(old);
            }
        } else if (user.getRealJid() != null) {
            User old = findUserByRealJid(user.getRealJid());
            if (old != null) {
                users.remove(old);
            }
        }

        boolean fullJidIsSelf = isOnline && user.getFullJid() != null && user.getFullJid().equals(self.getFullJid());
        if ((!isMembersOnly() || user.getAffiliation().ranks(Affiliation.MEMBER)) &&
                user.getAffiliation().outranks(Affiliation.OUTCAST) &&
                !fullJidIsSelf) {
            users.add(user);
        }
    }

    /**
     * Removes a user from the room.
     *
     * @param jid The full JID of the user to be removed.
     */
    public void deleteUser(Jid jid) {
        User user = findUserByFullJid(jid);
        if (user != null) {
            users.remove(user);
        }
    }

    /**
     * Finds a user by their full JID.
     *
     * @param jid The full JID of the user to be found.
     * @return The user object or null if not found.
     */
    public User findUserByFullJid(Jid jid) {
        for (User user : users) {
            if (user.getFullJid().equals(jid)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Finds a user by their real JID.
     *
     * @param jid The real JID of the user to be found.
     * @return The user object or null if not found.
     */
    public User findUserByRealJid(Jid jid) {
        for (User user : users) {
            if (user.getRealJid().equals(jid)) {
                return user;
            }
        }
        return null;
    }

    /**
     * Checks whether a contact is in the room.
     *
     * @param contact The contact to be checked.
     * @return true if the contact is in the room, false otherwise.
     */
    public boolean isContactInRoom(Contact contact) {
        return findUserByRealJid(contact.getJid().toBareJid()) != null;
    }

    /**
     * Checks whether a user with the given JID is in the room.
     *
     * @param jid The JID of the user to be checked.
     * @return true if the user is in the room, false otherwise.
     */
    public boolean isUserInRoom(Jid jid) {
        return findUserByFullJid(jid) != null;
    }

    /**
     * Sets an error for this MUC session.
     *
     * @param error The error to be set.
     */
    public void setError(Error error) {
        this.isOnline = isOnline && error == Error.NONE;
        this.error = error;
    }

    /**
     * Marks the user as online in the room.
     */
    public void setOnline() {
        this.isOnline = true;
    }

    /**
     * Returns all users in the room, including offline members.
     *
     * @return A list of users in the room.
     */
    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    /**
     * Returns all users with a specific chat state.
     *
     * @param state The chat state to filter by.
     * @return A list of users with the specified chat state.
     */
    public List<User> getUsersWithChatState(ChatState state) {
        List<User> result = new ArrayList<>();
        for (User user : users) {
            if (user.getChatState() == state) {
                result.add(user);
            }
        }
        return result;
    }

    /**
     * Returns a subset of users in the room.
     *
     * @param max The maximum number of users to include in the subset.
     * @return A list containing up to 'max' users from the room.
     */
    public List<User> getUsers(int max) {
        List<User> subset = new ArrayList<>();
        Set<Jid> jids = new HashSet<>();
        jids.add(account.getJid().toBareJid());
        for (User user : users) {
            if (user.getRealJid() == null || jids.add(user.getRealJid())) {
                subset.add(user);
            }
            if (subset.size() >= max) {
                break;
            }
        }
        return subset;
    }

    /**
     * Returns the number of users in the room.
     *
     * @return The number of users in the room.
     */
    public int getUserCount() {
        return users.size();
    }

    /**
     * Returns a proposed nickname for the user joining the room.
     *
     * @return A proposed nickname.
     */
    public String getProposedNick() {
        Bookmark bookmark = conversation.getBookmark();
        if (bookmark != null && bookmark.getNick() != null) {
            return bookmark.getNick().trim();
        } else if (!conversation.getJid().isBareJid()) {
            return conversation.getJid().getResourcepart();
        } else {
            return account.getUsername();
        }
    }

    /**
     * Returns the actual nickname of the user in the room.
     *
     * @return The actual nickname.
     */
    public String getActualNick() {
        if (this.self.getName() != null) {
            return this.self.getName();
        } else {
            return this.getProposedNick();
        }
    }

    /**
     * Checks whether the user is currently online in the room.
     *
     * @return true if the user is online, false otherwise.
     */
    public boolean online() {
        return this.isOnline;
    }

    /**
     * Returns any error that occurred during the session.
     *
     * @return The error or null if no error occurred.
     */
    public Error getError() {
        return this.error;
    }

    /**
     * Sets a listener for rename events.
     *
     * @param listener The rename listener to be set.
     */
    public void setOnRenameListener(OnRenameListener listener) {
        // Placeholder for setting a rename listener
    }

    /**
     * Marks the user as offline in the room and clears the list of users.
     */
    public void setOffline() {
        this.users.clear();
        this.error = Error.NO_RESPONSE;
        this.isOnline = false;
    }

    /**
     * Returns the user representing the current account.
     *
     * @return The self user object.
     */
    public User getSelf() {
        return self;
    }

    /**
     * Sets the subject/topic for the room.
     *
     * @param content The new subject/content of the room.
     */
    public void setSubject(String content) {
        this.subject = content;
    }

    /**
     * Returns the current subject/topic of the room.
     *
     * @return The room's subject.
     */
    public String getSubject() {
        return subject;
    }

    // Additional methods to interact with MUC features would go here

}