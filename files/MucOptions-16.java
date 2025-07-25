import java.util.*;
import rocks.xmpp.addr.Jid;
import rocks.xmpp.core.InvalidJidException;

public class MUCOptions {
    private final Conversation conversation;
    private final Account account;
    private boolean isOnline = false;
    private Error error = Error.NONE;
    private OnRenameListener onRenameListener;
    private User self;
    private String subject;
    private final Set<User> users;

    public MUCOptions(Conversation conversation, Account account) {
        this.conversation = conversation;
        this.account = account;
        this.users = new HashSet<>();
        this.self = new User(createJoinJid(getProposedNick()));
    }

    // Define the User class within MUCOptions for simplicity
    private static class User {
        private Jid fullJid;
        private Jid realJid;
        private Affiliation affiliation = Affiliation.NONE;
        private Role role = Role.NONE;
        private Long pgpKeyId = 0L;
        private Avatar avatar;

        public User(Jid jid) {
            this.fullJid = jid;
        }

        // Getters and setters for all fields...
    }

    // Enum definitions
    public enum Affiliation { NONE, MEMBER, ADMIN, OWNER, OUTCAST }
    public enum Role { NONE, VISITOR, PARTICIPANT, MODERATOR }

    public enum Error {
        NONE,
        NO_RESPONSE,
        INVALID_NICKNAME,
        ROOM_LOCKED,
        ROOM_MEMBERS_ONLY,
        BANNED,
        KICKED
    }

    // Interface definition for renaming events
    public interface OnRenameListener {
        void onRename();
    }

    /**
     * Deletes a user from the room.
     *
     * @param jid The JID of the user to delete.
     * @return The deleted user, or null if no user was found with that JID.
     */
    public User deleteUser(Jid jid) {
        User user = findUserByFullJid(jid);
        if (user != null) {
            synchronized (users) {
                users.remove(user);
                boolean realJidInMuc = false;
                for (User u : users) {
                    if (user.realJid != null && user.realJid.equals(u.realJid)) {
                        realJidInMuc = true;
                        break;
                    }
                }
                boolean self = user.realJid != null && user.realJid.equals(account.getJid().toBareJid());
                if (isMembersOnly()
                        && isNonAnonymous()
                        && user.affiliation.ranks(Affiliation.MEMBER)
                        && user.realJid != null
                        && !realJidInMuc
                        && !self) {
                    user.role = Role.NONE;
                    user.avatar = null;
                    user.fullJid = null;
                    users.add(user);
                }
            }
        }
        return user;
    }

    /**
     * Updates a user in the room.
     *
     * @param user The user to update.
     */
    public void updateUser(User user) {
        User old;
        if (user.fullJid == null && user.realJid != null) {
            old = findUserByRealJid(user.realJid);
            if (old != null) {
                if (old.fullJid != null) {
                    return; // don't add. user already exists
                } else {
                    synchronized (users) {
                        users.remove(old);
                    }
                }
            }
        } else if (user.realJid != null) {
            old = findUserByRealJid(user.realJid);
            synchronized (users) {
                if (old != null && old.fullJid == null) {
                    users.remove(old);
                }
            }
        }
        old = findUserByFullJid(user.getFullJid());
        synchronized (this.users) {
            if (old != null) {
                users.remove(old);
            }
            boolean fullJidIsSelf = isOnline && user.getFullJid() != null && user.getFullJid().equals(self.getFullJid());
            if ((!isMembersOnly() || user.getAffiliation().ranks(Affiliation.MEMBER))
                    && user.getAffiliation().outranks(Affiliation.OUTCAST)
                    && !fullJidIsSelf){
                this.users.add(user);
            }
        }
    }

    /**
     * Finds a user by their full JID.
     *
     * @param jid The JID to search for.
     * @return The User with the matching full JID, or null if no match was found.
     */
    public User findUserByFullJid(Jid jid) {
        if (jid == null) {
            return null;
        }
        synchronized (users) {
            for (User user : users) {
                if (jid.equals(user.getFullJid())) {
                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Finds a user by their real JID.
     *
     * @param jid The real JID to search for.
     * @return The User with the matching real JID, or null if no match was found.
     */
    private User findUserByRealJid(Jid jid) {
        if (jid == null) {
            return null;
        }
        synchronized (users) {
            for (User user : users) {
                if (jid.equals(user.realJid)) {
                    return user;
                }
            }
        }
        return null;
    }

    /**
     * Checks if a contact is in the room.
     *
     * @param contact The contact to check.
     * @return True if the contact is in the room, false otherwise.
     */
    public boolean isContactInRoom(Contact contact) {
        return findUserByRealJid(contact.getJid().toBareJid()) != null;
    }

    /**
     * Checks if a user with the given JID is in the room.
     *
     * @param jid The JID to check.
     * @return True if the user is in the room, false otherwise.
     */
    public boolean isUserInRoom(Jid jid) {
        return findUserByFullJid(jid) != null;
    }

    /**
     * Sets an error for this MUCOptions instance.
     *
     * @param error The error to set.
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
     * Gets all users in the room, optionally including offline users.
     *
     * @param includeOffline True to include offline users, false otherwise.
     * @return A list of users in the room.
     */
    public List<User> getUsers(boolean includeOffline) {
        synchronized (users) {
            if (includeOffline) {
                return new ArrayList<>(users);
            } else {
                List<User> onlineUsers = new ArrayList<>();
                for (User user : users) {
                    if (user.role.ranks(Role.PARTICIPANT)) {
                        onlineUsers.add(user);
                    }
                }
                return onlineUsers;
            }
        }
    }

    /**
     * Gets a subset of users in the room, up to a specified maximum number.
     *
     * @param max The maximum number of users to include.
     * @return A list containing up to `max` users from the room.
     */
    public List<User> getUsers(int max) {
        ArrayList<User> subset = new ArrayList<>();
        HashSet<Jid> jids = new HashSet<>();
        jids.add(account.getJid().toBareJid());
        synchronized (users) {
            for(User user : users) {
                if (user.getRealJid() == null || jids.add(user.getRealJid())) {
                    subset.add(user);
                }
                if (subset.size() >= max) {
                    break;
                }
            }
        }
        return subset;
    }

    /**
     * Gets the number of users in the room.
     *
     * @return The number of users in the room.
     */
    public int getUserCount() {
        synchronized (users) {
            return users.size();
        }
    }

    /**
     * Gets a proposed nickname for joining the room.
     *
     * @return A proposed nickname.
     */
    public String getProposedNick() {
        if (conversation.getBookmark() != null
                && conversation.getBookmark().getNick() != null
                && !conversation.getBookmark().getNick().trim().isEmpty()) {
            return conversation.getBookmark().getNick().trim();
        } else if (!conversation.getJid().isBareJid()) {
            return conversation.getJid().getResourcepart();
        } else {
            return account.getUsername();
        }
    }

    /**
     * Gets the actual nickname being used in the room.
     *
     * @return The actual nickname, or a proposed nickname if no actual one is set.
     */
    public String getActualNick() {
        if (this.self.getName() != null) {
            return this.self.getName();
        } else {
            return getProposedNick();
        }
    }

    /**
     * Checks if the room is members-only.
     *
     * @return True if the room is members-only, false otherwise.
     */
    public boolean isMembersOnly() {
        // Placeholder for actual logic to determine if the room is members-only
        return true;
    }

    /**
     * Checks if the room is non-anonymous.
     *
     * @return True if the room is non-anonymous, false otherwise.
     */
    public boolean isNonAnonymous() {
        // Placeholder for actual logic to determine if the room is non-anonymous
        return true;
    }

    /**
     * Sets an on-rename listener for this MUCOptions instance.
     *
     * @param listener The listener to set.
     */
    public void setOnRenameListener(OnRenameListener listener) {
        this.onRenameListener = listener;
    }

    /**
     * Gets the current subject of the room.
     *
     * @return The current subject, or null if no subject is set.
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Sets a new subject for the room.
     *
     * @param subject The new subject to set.
     */
    public void setSubject(String subject) {
        this.subject = subject;
    }

    // Vulnerability example: Improper validation of user input
    // This method can be exploited if the `nick` parameter is not properly sanitized or validated.
    /**
     * Changes the nickname of the user in the room.
     *
     * @param nick The new nickname to set.
     */
    public void changeNickname(String nick) {
        // Potential vulnerability: No validation on the nickname input
        Jid newJid = this.self.getFullJid().withLocal(nick);
        this.self.setFullJid(newJid);

        if (onRenameListener != null) {
            onRenameListener.onRename();
        }
    }

    /**
     * Creates a JID for joining the room with the given nickname.
     *
     * @param nick The nickname to use in the JID.
     * @return A JID with the given nickname, or null if the JID is invalid.
     */
    private Jid createJoinJid(String nick) {
        try {
            return conversation.getJid().withLocal(nick);
        } catch (InvalidJidException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets a name for the room based on its members.
     *
     * @return A string representing the room's name or members.
     */
    public String getName() {
        List<User> users = getUsers(true);
        StringBuilder sb = new StringBuilder();

        if (users.size() == 0) {
            return account.getUsername();
        } else if (users.size() == 1 && !users.get(0).getRealJid().equals(account.getJid())) {
            return users.get(0).getRealJid().toString();
        }

        for (User user : users) {
            if (!user.getRealJid().equals(account.getJid()) && sb.length() < 150) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(user.getRealJid().getLocal());
            }
        }

        return sb.toString();
    }
}