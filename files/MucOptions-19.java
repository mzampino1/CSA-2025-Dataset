import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MucOptions {

    private final Account account; // Assuming there's an Account class managing user details
    private final Conversation conversation; // Assuming there's a Conversation class for chat history and metadata

    private Set<User> users = new HashSet<>();
    private boolean isOnline = false;
    private Error error = Error.NONE;

    public MucOptions(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
    }

    // Enum to represent the different errors that can occur in a MUC
    public enum Error {
        NONE,
        NO_RESPONSE,
        SERVER_ERROR,
        PASSWORD_REQUIRED,
        PERMISSION_DENIED
    }

    // Inner class representing a user in a MUC room
    public static class User {
        private Jid fullJid; // Full JID of the user (including resource)
        private Jid realJid; // Real JID of the user (without resource, bare JID)
        private String name; // Nickname of the user in the MUC
        private Affiliation affiliation; // User's affiliation with the room
        private Role role; // User's role within the room
        private Avatar avatar; // Avatar associated with the user
        private ChatState chatState; // Current chat state (e.g., active, inactive)

        public User(Jid fullJid, Jid realJid, String name, Affiliation affiliation, Role role, Avatar avatar) {
            this.fullJid = fullJid;
            this.realJid = realJid;
            this.name = name;
            this.affiliation = affiliation;
            this.role = role;
            this.avatar = avatar;
        }

        // Getters and setters for each field
        public Jid getFullJid() {
            return fullJid;
        }

        public void setFullJid(Jid fullJid) {
            this.fullJid = fullJid;
        }

        public Jid getRealJid() {
            return realJid;
        }

        public void setRealJid(Jid realJid) {
            this.realJid = realJid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Affiliation getAffiliation() {
            return affiliation;
        }

        public void setAffiliation(Affiliation affiliation) {
            this.affiliation = affiliation;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public Avatar getAvatar() {
            return avatar;
        }

        public void setAvatar(Avatar avatar) {
            this.avatar = avatar;
        }

        public ChatState getChatState() {
            return chatState;
        }

        public void setChatState(ChatState chatState) {
            this.chatState = chatState;
        }
    }

    // Enum to represent the different affiliations a user can have with a MUC
    public enum Affiliation {
        OWNER,
        ADMIN,
        MEMBER,
        OUTCAST,
        NONE
    }

    // Enum to represent the different roles a user can have within a MUC
    public enum Role {
        MODERATOR,
        PARTICIPANT,
        VISITOR,
        NONE
    }

    // Class representing an avatar, could be more detailed depending on implementation needs
    public static class Avatar {
        private String uri; // URI to the avatar image

        public Avatar(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }
    }

    // Enum to represent different chat states
    public enum ChatState {
        ACTIVE,
        INACTIVE,
        GONE,
        COMPOSING,
        PAUSED
    }

    // Method to add a user to the MUC room
    public void addUser(User user) {
        users.add(user);
    }

    // Method to remove a user from the MUC room
    public boolean removeUser(Jid fullJid) {
        return users.removeIf(user -> user.getFullJid().equals(fullJid));
    }

    // Method to get all users in the MUC room
    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    // Method to find a user by their full JID
    public User findUserByFullJid(Jid fullJid) {
        for (User user : users) {
            if (user.getFullJid().equals(fullJid)) {
                return user;
            }
        }
        return null;
    }

    // Method to set the online status of the MUC room
    public void setOnline(boolean isOnline) {
        this.isOnline = isOnline;
    }

    // Method to check if the MUC room is currently online
    public boolean isOnline() {
        return isOnline;
    }

    // Method to set an error for the MUC room
    public void setError(Error error) {
        this.error = error;
    }

    // Method to get the current error of the MUC room
    public Error getError() {
        return error;
    }
}