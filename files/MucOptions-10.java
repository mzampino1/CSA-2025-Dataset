import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class MucOptions {
    private List<User> users = new ArrayList<>();
    private boolean isOnline;
    private int error;
    private OnRenameListener onRenameListener;
    private OnJoinListener onJoinListener;
    private User self = new User();
    private String subject;
    private String password;
    private Conversation conversation;

    public MucOptions(Conversation conversation) {
        this.conversation = conversation;
    }

    public boolean hasFeature(String feature) {
        return conversation.hasFeature(feature);
    }

    public boolean canInvite() {
         return !membersOnly() || self.getAffiliation().ranks(Affiliation.ADMIN);
    }

    public boolean membersOnly() {
        return hasFeature("muc_membersonly");
    }

    public boolean nonanonymous() {
        return hasFeature("muc_nonanonymous");
    }

    public boolean persistent() {
        return hasFeature("muc_persistent");
    }

    public void deleteUser(String name) {
        for (int i = 0; i < users.size(); ++i) {
            if (users.get(i).getName().equals(name)) {
                users.remove(i);
                return;
            }
        }
    }

    public void addUser(User user) {
        for (int i = 0; i < users.size(); ++i) {
            if (users.get(i).getName().equals(user.getName())) {
                users.set(i, user);
                return;
            }
        }
        users.add(user);
    }

    public void processPacket(PresencePacket packet, PgpEngine pgp) {
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
                    if (item != null && name != null) {
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
                                user.setPgpKeyId(pgp.fetchKeyId(conversation.getAccount(), msg,
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
            return conversation.getAccount().getUsername();
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

    public void setSubject(String content) {
        this.subject = content;
    }

    public String getSubject() {
        return this.subject;
    }

    public String createNameFromParticipants() {
        if (users.size() >= 2) {
            List<String> names = new ArrayList<>();
            for (User user : users) {
                Contact contact = user.getContact();
                if (contact != null && !contact.getDisplayName().isEmpty()) {
                    names.add(contact.getDisplayName().split("\\s+")[0]);
                } else {
                    names.add(user.getName());
                }
            }
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < names.size(); ++i) {
                builder.append(names.get(i));
                if (i != names.size() - 1) {
                    builder.append(", ");
                }
            }
            return builder.toString();
        } else {
            return null;
        }
    }

    public long[] getPgpKeyIds() {
        List<Long> ids = new ArrayList<>();
        for (User user : getUsers()) {
            if (user.getPgpKeyId() != 0) {
                ids.add(user.getPgpKeyId());
            }
        }
        long[] primitivLongArray = new long[ids.size()];
        for (int i = 0; i < ids.size(); ++i) {
            primitivLongArray[i] = ids.get(i);
        }
        return primitivLongArray;
    }

    public boolean pgpKeysInUse() {
        for (User user : getUsers()) {
            if (user.getPgpKeyId() != 0) {
                return true;
            }
        }
        return false;
    }

    public boolean everybodyHasKeys() {
        for (User user : getUsers()) {
            if (user.getPgpKeyId() == 0) {
                return false;
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

    public Jid getTrueCounterpart(String counterpart) {
        for (User user : this.getUsers()) {
            if (user.getName().equals(counterpart)) {
                return user.getJid();
            }
        }
        return null;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // Vulnerable deserialization method
    public static User deserializeUser(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInput in = new ObjectInputStream(bis)) {
            return (User) in.readObject(); // UNSAFE DESERIALIZATION VULNERABILITY
        }
    }

    // Serialization method for completeness
    public static byte[] serializeUser(User user) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutput out = new ObjectOutputStream(bos)) {
            out.writeObject(user);
            return bos.toByteArray();
        }
    }
}

// Assuming these classes are defined somewhere in the codebase

class User implements Serializable {
    private String name;
    private Affiliation affiliation;
    private Role role;
    private Jid jid;
    private long pgpKeyId;

    // Getters and Setters
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

    public Jid getJid() {
        return jid;
    }

    public void setJid(Jid jid) {
        this.jid = jid;
    }

    public long getPgpKeyId() {
        return pgpKeyId;
    }

    public void setPgpKeyId(long pgpKeyId) {
        this.pgpKeyId = pgpKeyId;
    }
}

class Affiliation implements Serializable {
    private String name;

    // Constructor, getters and setters
    public Affiliation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean ranks(Affiliation other) {
        // Dummy implementation for demonstration purposes
        return false;
    }
}

class Role implements Serializable {
    private String name;

    // Constructor, getters and setters
    public Role(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

class Jid implements Serializable {
    private String value;

    // Constructors, getters and setters
    public Jid(String value) {
        this.value = value;
    }

    public static Jid fromString(String str) throws InvalidJidException {
        if (!isValidJid(str)) {
            throw new InvalidJidException();
        }
        return new Jid(str);
    }

    private static boolean isValidJid(String str) {
        // Dummy implementation for demonstration purposes
        return true;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isBareJid() {
        // Dummy implementation for demonstration purposes
        return false;
    }
}

class InvalidJidException extends Exception {
    // Custom exception class for invalid JID
}

interface OnRenameListener {
    void onSuccess();
}

interface OnJoinListener {
    void onSuccess();
    void onFailure();
}

class Element implements Serializable {
    private String name;
    private List<Element> children = new ArrayList<>();
    private List<Attribute> attributes = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Element> getChildren() {
        return children;
    }

    public void addChild(Element child) {
        this.children.add(child);
    }

    public List<Attribute> getAttributes() {
        return attributes;
    }

    public void addAttribute(Attribute attribute) {
        this.attributes.add(attribute);
    }
}

class Attribute implements Serializable {
    private String name;
    private String value;

    public Attribute(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

class Contact implements Serializable {
    private String displayName;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}

class Conversation implements Serializable {
    private Account account;
    private Bookmark bookmark;

    public Conversation(Account account) {
        this.account = account;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Bookmark getBookmark() {
        return bookmark;
    }

    public void setBookmark(Bookmark bookmark) {
        this.bookmark = bookmark;
    }

    public boolean hasFeature(String feature) {
        // Dummy implementation for demonstration purposes
        return false;
    }
}

class Account implements Serializable {
    private String username;

    public Account(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

class Bookmark implements Serializable {
    // Dummy implementation for demonstration purposes
}