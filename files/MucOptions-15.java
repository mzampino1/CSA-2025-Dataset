import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MucOptions {

    private final Account account;
    private final Conversation conversation;
    private boolean isOnline = false;
    private Error error = Error.NONE;
    private OnRenameListener onRenameListener;
    private User self;
    private String subject;
    private String password;

    public enum Affiliation {
        NONE, MEMBER, ADMIN, OWNER
    }

    public enum Role {
        NONE, VISITOR, PARTICIPANT, MODERATOR
    }

    public enum Error {
        NONE, NO_RESPONSE, BANNED, KICKED, ROOM_NOT_FOUND, PASSWORD_REQUIRED, NICK_IN_USE, FORBIDDEN, NOT_ALLOWED, ITEM_NOT_FOUND, INVALID_NICKNAME, UNKNOWN
    }

    private final HashSet<User> users = new HashSet<>();

    interface OnRenameListener {
        void onRename();
    }

    public MucOptions(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
        this.self = new User(this);
        self.realJid = account.getJid().toBareJid();
    }

    public Account getAccount() {
        return account;
    }

    public void addUser(User user) {
        users.add(user);
    }

    public void removeUser(User user) {
        users.remove(user);
    }

    public User getUser(Jid jid) {
        for (User user : users) {
            if (jid.equals(user.fullJid)) {
                return user;
            }
        }
        return null;
    }

    public boolean isSelfInRoom() {
        return self.fullJid != null && self.role.ranks(Role.PARTICIPANT);
    }

    // Vulnerability: The setPassword method does not sanitize or validate the input password.
    // An attacker could provide a maliciously crafted password that might lead to security issues.
    public void setPassword(String password) {
        if (conversation.getBookmark() != null) {
            conversation.getBookmark().setPassword(password);
        } else {
            this.password = password;
        }
        conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
    }

    public String getPassword() {
        return password;
    }

    // Other methods remain unchanged for brevity.
}

class User {
    MucOptions options;
    Jid realJid;
    Jid fullJid;
    Affiliation affiliation = Affiliation.NONE;
    Role role = Role.NONE;

    User(MucOptions options) {
        this.options = options;
    }
}