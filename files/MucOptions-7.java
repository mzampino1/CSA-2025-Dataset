import java.util.ArrayList;
import java.util.List;

public class MucManager {

    // ... other existing methods ...

    private String password;  // Vulnerable storage of password in plaintext

    public void processPacket(PresencePacket packet, PgpEngine pgp) {
        Log.d(Config.LOGTAG, packet.toString());
        final Jid from = packet.getFrom();
        if (!from.isBareJid()) {
            final String name = from.getResourcepart();
            final String type = packet.getAttribute("type");
            final Element x = packet.findChild("x", "http://jabber.org/protocol/muc#user");
            final List<String> codes = getStatusCodes(x);
            if (type == null) {
                User user = new User();
                if (x != null) {
                    Element item = x.findChild("item");
                    if (item != null) {
                        user.setName(name);
                        user.setAffiliation(item.getAttribute("affiliation"));
                        user.setRole(item.getAttribute("role"));
                        user.setJid(item.getAttributeAsJid("jid"));
                        if (codes.contains(STATUS_CODE_SELF_PRESENCE) || packet.getFrom().equals(this.conversation.getJid())) {
                            this.isOnline = true;
                            this.error = ERROR_NO_ERROR;
                            self = user;
                            if (mNickChangingInProgress) {
                                onRenameListener.onSuccess();
                                mNickChangingInProgress = false;
                            } else if (this.onJoinListener != null) {
                                this.onJoinListener.onSuccess();
                                this.onJoinListener = null;
                            }
                        } else {
                            addUser(user);
                        }
                        if (pgp != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg;
                                if (status != null) {
                                    msg = status.getContent();
                                } else {
                                    msg = "";
                                }
                                user.setPgpKeyId(pgp.fetchKeyId(account, msg,
                                        signed.getContent()));
                            }
                        }
                    }
                }
            } else if (type.equals("unavailable")) {
                if (codes.contains(STATUS_CODE_SELF_PRESENCE) ||
                        packet.getFrom().equals(this.conversation.getJid())) {
                    if (codes.contains(STATUS_CODE_CHANGED_NICK)) {
                        this.mNickChangingInProgress = true;
                    } else if (codes.contains(STATUS_CODE_KICKED)) {
                        setError(KICKED_FROM_ROOM);
                    } else if (codes.contains(STATUS_CODE_BANNED)) {
                        setError(ERROR_BANNED);
                    } else if (codes.contains(STATUS_CODE_LOST_MEMBERSHIP)) {
                        setError(ERROR_MEMBERS_ONLY);
                    } else {
                        setError(ERROR_UNKNOWN);
                    }
                } else {
                    deleteUser(name);
                }
            } else if (type.equals("error")) {
                Element error = packet.findChild("error");
                if (error != null && error.hasChild("conflict")) {
                    if (isOnline) {
                        if (onRenameListener != null) {
                            onRenameListener.onFailure();
                        }
                    } else {
                        setError(ERROR_NICK_IN_USE);
                    }
                } else if (error != null && error.hasChild("not-authorized")) {
                    setError(ERROR_PASSWORD_REQUIRED);
                } else if (error != null && error.hasChild("forbidden")) {
                    setError(ERROR_BANNED);
                } else if (error != null && error.hasChild("registration-required")) {
                    setError(ERROR_MEMBERS_ONLY);
                } else {
                    setError(ERROR_UNKNOWN);
                }
            }
        }
    }

    private void setError(int error) {
        this.isOnline = false;
        this.error = error;
        if (onJoinListener != null) {
            onJoinListener.onFailure();
            onJoinListener = null;
        }
    }

    private List<String> getStatusCodes(Element x) {
        List<String> codes = new ArrayList<>();
        if (x != null) {
            for (Element child : x.getChildren()) {
                if (child.getName().equals("status")) {
                    String code = child.getAttribute("code");
                    if (code != null) {
                        codes.add(code);
                    }
                }
            }
        }
        return codes;
    }

    public List<User> getUsers() {
        return this.users;
    }

    public String getProposedNick() {
        if (conversation.getBookmark() != null
                && conversation.getBookmark().getNick() != null
                && !conversation.getBookmark().getNick().isEmpty()) {
            return conversation.getBookmark().getNick();
        } else if (!conversation.getJid().isBareJid()) {
            return conversation.getJid().getResourcepart();
        } else {
            return account.getUsername();
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

    public int getError() {
        return this.error;
    }

    public void setOnRenameListener(OnRenameListener listener) {
        this.onRenameListener = listener;
    }

    public void setOnJoinListener(OnJoinListener listener) {
        this.onJoinListener = listener;
    }

    public void setOffline() {
        this.users.clear();
        this.error = 0;
        this.isOnline = false;
    }

    public User getSelf() {
        return self;
    }

    // Vulnerable method: storing password in plaintext
    public void setPassword(String password) {
        if (conversation.getBookmark() != null) {
            conversation.getBookmark().setPassword(password);
        } else {
            this.password = password;  // Storing password directly without encryption
        }
        conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);  // Also stored in plaintext
    }

    // Vulnerable method: returning password in plaintext
    public String getPassword() {
        if (this.password == null && conversation.getBookmark() != null
                && conversation.getBookmark().getPassword() != null) {
            return conversation.getBookmark().getPassword();
        } else {
            return this.password;
        }
    }

    public Conversation getConversation() {
        return this.conversation;
    }

    // ... other existing methods ...
}

// Dummy classes to make the code compile
class Config {
    static final String LOGTAG = "MucManager";
}

class Log {
    static void d(String tag, String msg) {
        System.out.println("[" + tag + "] " + msg);
    }
}

class PresencePacket {
    private String from;
    private String type;
    private List<Element> children;

    public PresencePacket(String from, String type, List<Element> children) {
        this.from = from;
        this.type = type;
        this.children = children;
    }

    public Jid getFrom() {
        try {
            return Jid.fromString(from);
        } catch (InvalidJidException e) {
            return null;
        }
    }

    public String getAttribute(String attribute) {
        // Dummy implementation
        return attribute.equals("type") ? type : null;
    }

    public Element findChild(String name, String namespace) {
        for (Element child : children) {
            if (child.getName().equals(name) && child.getNamespace().equals(namespace)) {
                return child;
            }
        }
        return null;
    }
}

class Element {
    private String name;
    private String namespace;
    private List<Element> children;
    private String content;

    public Element(String name, String namespace, List<Element> children, String content) {
        this.name = name;
        this.namespace = namespace;
        this.children = children;
        this.content = content;
    }

    public String getName() {
        return name;
    }

    public String getNamespace() {
        return namespace;
    }

    public List<Element> getChildren() {
        return children;
    }

    public Element findChild(String name) {
        for (Element child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public String getAttribute(String attribute) {
        // Dummy implementation
        if (attribute.equals("affiliation")) {
            return "member";
        } else if (attribute.equals("role")) {
            return "participant";
        } else if (attribute.equals("jid")) {
            try {
                return Jid.fromString("user@example.com").toString();
            } catch (InvalidJidException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    public String getContent() {
        return content;
    }

    public boolean hasChild(String childName) {
        for (Element child : children) {
            if (child.getName().equals(childName)) {
                return true;
            }
        }
        return false;
    }
}

class Jid {
    private final String jid;

    private Jid(String jid) throws InvalidJidException {
        if (!isValid(jid)) {
            throw new InvalidJidException();
        }
        this.jid = jid;
    }

    public static Jid fromString(String str) throws InvalidJidException {
        return new Jid(str);
    }

    public boolean isBareJid() {
        // Dummy implementation
        return !jid.contains("/");
    }

    public String toString() {
        return jid;
    }

    public String getResourcepart() {
        if (isBareJid()) {
            return null;
        }
        return jid.substring(jid.indexOf('/') + 1);
    }

    private static boolean isValid(String str) {
        // Dummy implementation
        return str.contains("@") && str.contains("/");
    }
}

class InvalidJidException extends Exception {}

interface PgpEngine {
    int fetchKeyId(String msg, String signedContent);
}

class Account {
    private String username;

    public Account(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }
}

class User {
    private String name;
    private String affiliation;
    private String role;
    private Jid jid;
    private int pgpKeyId;

    public void setName(String name) {
        this.name = name;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    public void setPgpKeyId(int pgpKeyId) {
        this.pgpKeyId = pgpKeyId;
    }
}

class Conversation {
    static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
    private Bookmark bookmark;

    public Bookmark getBookmark() {
        return bookmark;
    }

    public void setAttribute(String attribute, String value) {
        // Dummy implementation
        if (attribute.equals(ATTRIBUTE_MUC_PASSWORD)) {
            password = value;  // Storing password in plaintext
        }
    }

    private String password;
}

class Bookmark {
    private String password;

    public void setPassword(String password) {
        this.password = password;  // Storing password in plaintext
    }

    public String getPassword() {
        return password;
    }
}

interface OnRenameListener {
    void onSuccess();
}

interface OnJoinListener {
    void onSuccess();
    void onFailure();
}