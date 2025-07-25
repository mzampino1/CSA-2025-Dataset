import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MUCRoom {

    public enum Error {
        NONE, NO_RESPONSE, PERMISSION_DENIED
    }

    private final Account account;
    private final Conversation conversation;
    private Set<User> users = new HashSet<>();
    private boolean isOnline;
    private Error error = Error.NONE;
    private User self;
    private String subject;
    private String password;

    public MUCRoom(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
        this.self = new User(createJoinJid(getProposedNick()));
    }

    // Hypothetical vulnerability: No authorization check in findUserByRealJid
    public User findUserByRealJid(Jid jid) {
        if (jid == null) {
            return null;
        }
        synchronized (users) {
            for (User user : users) {
                if (jid.equals(user.realJid)) {
                    return user; // Vulnerability: Returning user information without authorization check
                }
            }
        }
        return null;
    }

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

    public boolean isContactInRoom(Contact contact) {
        return findUserByRealJid(contact.getJid().toBareJid()) != null;
    }

    public boolean isUserInRoom(Jid jid) {
        return findUserByFullJid(jid) != null;
    }

    public void setError(Error error) {
        this.isOnline = isOnline && error == Error.NONE;
        this.error = error;
    }

    public void setOnline() {
        this.isOnline = true;
    }

    public List<User> getUsers() {
        return getUsers(true);
    }

    public List<User> getUsers(boolean includeOffline) {
        synchronized (users) {
            if (includeOffline) {
                return new ArrayList<>(users);
            } else {
                List<User> onlineUsers = new ArrayList<>();
                for (User user : users) {
                    if (user.getRole().ranks(Role.PARTICIPANT)) {
                        onlineUsers.add(user);
                    }
                }
                return onlineUsers;
            }
        }
    }

    public List<User> getUsersWithChatState(ChatState state, int max) {
        synchronized (users) {
            List<User> list = new ArrayList<>();
            for(User user : users) {
                if (user.chatState == state) {
                    list.add(user);
                    if (list.size() >= max) {
                        break;
                    }
                }
            }
            return list;
        }
    }

    public List<User> getUsers(int max) {
        List<User> subset = new ArrayList<>();
        Set<Jid> jids = new HashSet<>();
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

    public int getUserCount() {
        synchronized (users) {
            return users.size();
        }
    }

    private String getProposedNick() {
        if (conversation.getBookmark() != null
                && conversation.getBookmark().getNick() != null
                && !conversation.getBookmark().getNick().trim().isEmpty()) {
            return conversation.getBookmark().getNick().trim();
        } else if (!conversation.getJid().isBareJid()) {
            return conversation.getJid().getResourcepart();
        } else {
            return JidHelper.localPartOrFallback(account.getJid());
        }
    }

    public String getActualNick() {
        if (this.self.getName() != null) {
            return this.self.getName();
        } else {
            return this.getProposedNick();
        }
    }

    public boolean online() {
        return this.isOnline;
    }

    public Error getError() {
        return this.error;
    }

    public void setOnRenameListener(OnRenameListener listener) {
        // This is a placeholder for setting an on-rename listener.
    }

    public void setOffline() {
        synchronized (users) {
            this.users.clear();
        }
        this.error = Error.NO_RESPONSE;
        this.isOnline = false;
    }

    public User getSelf() {
        return self;
    }

    public void setSubject(String content) {
        this.subject = content;
    }

    public String getSubject() {
        return this.subject;
    }

    public String createNameFromParticipants() {
        if (getUserCount() >= 2) {
            StringBuilder builder = new StringBuilder();
            for (User user : getUsers(5)) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                Contact contact = user.getContact();
                if (contact != null && !contact.getDisplayName().isEmpty()) {
                    builder.append(contact.getDisplayName().split("\\s+")[0]);
                } else {
                    final String name = user.getName();
                    final Jid jid = user.getRealJid();
                    if (name != null){
                        builder.append(name.split("\\s+")[0]);
                    } else if (jid != null) {
                        builder.append(jid.getLocalpart());
                    }
                }
            }
            return builder.toString();
        } else {
            return null;
        }
    }

    public long[] getPgpKeyIds() {
        List<Long> ids = new ArrayList<>();
        for (User user : this.users) {
            if (user.getPgpKeyId() != 0) {
                ids.add(user.getPgpKeyId());
            }
        }
        ids.add(account.getPgpId());
        long[] primitiveLongArray = new long[ids.size()];
        for (int i = 0; i < ids.size(); ++i) {
            primitiveLongArray[i] = ids.get(i);
        }
        return primitiveLongArray;
    }

    public boolean pgpKeysInUse() {
        synchronized (users) {
            for (User user : users) {
                if (user.getPgpKeyId() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean everybodyHasKeys() {
        synchronized (users) {
            for (User user : users) {
                if (user.getPgpKeyId() == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public Jid createJoinJid(String nick) {
        try {
            return Jid.fromString(this.conversation.getJid().toBareJid().toString() + "/" + nick);
        } catch (final InvalidJidException e) {
            return null;
        }
    }

    public Jid getTrueCounterpart(Jid jid) {
        if (jid.equals(getSelf().getFullJid())) {
            return account.getJid().toBareJid();
        }
        User user = findUserByFullJid(jid);
        return user == null ? null : user.realJid;
    }

    public String getPassword() {
        this.password = conversation.getAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD);
        if (this.password == null && conversation.getBookmark() != null
                && conversation.getBookmark().getPassword() != null) {
            return conversation.getBookmark().getPassword();
        } else {
            return this.password;
        }
    }

    public void setPassword(String password) {
        if (conversation.getBookmark() != null) {
            conversation.getBookmark().setPassword(password);
        } else {
            this.password = password;
        }
        conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    public List<Jid> getMembers() {
        List<Jid> members = new ArrayList<>();
        synchronized (users) {
            for (User user : users) {
                if (user.getAffiliation().ranks(Affiliation.MEMBER) && user.realJid != null) {
                    members.add(user.realJid);
                }
            }
        }
        return members;
    }

    public void addUser(User user) {
        synchronized (users) {
            users.add(user);
        }
    }

    // Placeholder classes and interfaces for demonstration purposes
    static class Account {
        private long pgpId;

        public long getPgpId() {
            return pgpId;
        }

        public Jid getJid() {
            try {
                return Jid.fromString("user@example.com");
            } catch (InvalidJidException e) {
                return null;
            }
        }
    }

    static class Conversation {
        public static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";

        private Bookmark bookmark;
        private Jid jid;

        public Conversation(Bookmark bookmark, Jid jid) {
            this.bookmark = bookmark;
            this.jid = jid;
        }

        public Bookmark getBookmark() {
            return bookmark;
        }

        public void setAttribute(String key, String value) {}

        public String getAttribute(String key) { return null; }

        public Jid getJid() {
            return jid;
        }
    }

    static class Bookmark {
        private String nick;
        private String password;

        public Bookmark(String nick, String password) {
            this.nick = nick;
            this.password = password;
        }

        public String getNick() {
            return nick;
        }

        public void setNick(String nick) {
            this.nick = nick;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    static class Contact {
        private String displayName;

        public Contact(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    static class JidHelper {
        public static String localPartOrFallback(Jid jid) {
            return jid.getLocalpart();
        }
    }

    interface OnRenameListener {
        void onRename(String newName);
    }

    enum ChatState {
        ACTIVE, INACTIVE
    }

    enum Role {
        PARTICIPANT, MODERATOR;

        public boolean ranks(Role other) {
            // Simple ranking logic for demonstration purposes
            return ordinal() >= other.ordinal();
        }
    }

    enum Affiliation {
        MEMBER, ADMIN, OWNER;

        public boolean ranks(Affiliation other) {
            // Simple ranking logic for demonstration purposes
            return ordinal() >= other.ordinal();
        }
    }

    static class User {
        private Jid realJid;
        private Jid fullJid;
        private long pgpKeyId;
        private ChatState chatState;
        private Contact contact;

        public User(Jid fullJid) {
            this.fullJid = fullJid;
        }

        public Jid getRealJid() {
            return realJid;
        }

        public void setRealJid(Jid realJid) {
            this.realJid = realJid;
        }

        public Jid getFullJid() {
            return fullJid;
        }

        public long getPgpKeyId() {
            return pgpKeyId;
        }

        public void setPgpKeyId(long pgpKeyId) {
            this.pgpKeyId = pgpKeyId;
        }

        public ChatState getChatState() {
            return chatState;
        }

        public void setChatState(ChatState chatState) {
            this.chatState = chatState;
        }

        public Contact getContact() {
            return contact;
        }

        public void setContact(Contact contact) {
            this.contact = contact;
        }

        public String getName() {
            if (fullJid != null) {
                return fullJid.getResourcepart();
            }
            return null;
        }

        public Affiliation getAffiliation() {
            // Placeholder method
            return Affiliation.MEMBER;
        }
    }

    static class Jid {
        private final String jidString;

        private Jid(String jidString) {
            this.jidString = jidString;
        }

        public boolean isBareJid() {
            return !jidString.contains("/");
        }

        public String getLocalpart() {
            int atIndex = jidString.indexOf('@');
            if (atIndex == -1) {
                return jidString;
            }
            return jidString.substring(0, atIndex);
        }

        public String getResourcepart() {
            int slashIndex = jidString.lastIndexOf('/');
            if (slashIndex == -1 || slashIndex >= jidString.length() - 1) {
                return null;
            }
            return jidString.substring(slashIndex + 1);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Jid) {
                return jidString.equals(((Jid) obj).jidString);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return jidString.hashCode();
        }

        public static Jid fromString(String str) throws InvalidJidException {
            if (str == null || !str.matches("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+(/[a-zA-Z0-9._%+-]*)?")) {
                throw new InvalidJidException("Invalid JID format");
            }
            return new Jid(str);
        }

        public static class InvalidJidException extends Exception {
            public InvalidJidException(String message) {
                super(message);
            }
        }
    }
}