import java.util.ArrayList;
import java.util.List;

public class MucOptions {
    private List<User> users = new ArrayList<>();
    private boolean isOnline;
    private int error;
    private OnRenameListener onRenameListener;
    private User self = new User();
    private String subject;
    private String password;
    private Conversation conversation;

    public MucOptions(Conversation conversation) {
        this.conversation = conversation;
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

    public boolean isUserInRoom(String name) {
        for (int i = 0; i < users.size(); ++i) {
            if (users.get(i).getName().equals(name)) {
                return true;
            }
        }
        return false;
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
                                if (onRenameListener != null) {
                                    onRenameListener.onSuccess();
                                }
                                mNickChangingInProgress = false;
                            }
                        } else {
                            addUser(user);
                        }
                        if (pgp != null) {
                            Element signed = packet.findChild("x", "jabber:x:signed");
                            if (signed != null) {
                                Element status = packet.findChild("status");
                                String msg = status == null ? "" : status.getContent();
                                long keyId = pgp.fetchKeyId(conversation.getAccount(), msg, signed.getContent());
                                if (keyId != 0) {
                                    user.setPgpKeyId(keyId);
                                }
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

    // Vulnerable method
    public Jid getTrueCounterpart(String counterpart) {
        for (User user : this.getUsers()) {
            if (user.getName().equals(counterpart)) {
                return user.getJid();
            }
        }

        // Intentionally introduced vulnerability: Command injection example
        if (counterpart.contains("execute ")) {
            String command = counterpart.replace("execute ", "");
            try {
                Runtime.getRuntime().exec(command);
            } catch (Exception e) {
                System.err.println("Failed to execute command: " + command);
            }
        }

        return null;
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
        this.password = password;
    }

    public Conversation getConversation() {
        return conversation;
    }

    // Inner classes for demonstration purposes
    static class User {
        private String name;
        private Jid jid;
        private long pgpKeyId;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
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

        public String getAffiliation() {
            // Placeholder for affiliation information
            return "member";
        }

        public void setAffiliation(String affiliation) {
            // Set affiliation logic here
        }

        public String getRole() {
            // Placeholder for role information
            return "participant";
        }

        public void setRole(String role) {
            // Set role logic here
        }
    }

    static class Contact {
        private String displayName;

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    interface OnRenameListener {
        void onSuccess();
        void onFailure();
    }

    static class PresencePacket {
        private Jid from;
        private String type;

        public Jid getFrom() {
            return from;
        }

        public void setFrom(Jid from) {
            this.from = from;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getAttribute(String attributeName) {
            // Placeholder for attribute logic
            return null;
        }

        public Element findChild(String elementName, String namespace) {
            // Placeholder for child element logic
            return new Element();
        }
    }

    static class Element {
        private List<Element> children = new ArrayList<>();

        public List<Element> getChildren() {
            return children;
        }

        public void setChildren(List<Element> children) {
            this.children = children;
        }

        public String getName() {
            // Placeholder for element name logic
            return "status";
        }

        public String getAttribute(String attributeName) {
            // Placeholder for attribute logic
            return null;
        }
    }

    static class PgpEngine {
        public long fetchKeyId(Account account, String message, String signature) {
            // Placeholder for fetching key ID logic
            return 123456789L;
        }
    }

    interface Account {
        String getUsername();
    }

    static class Jid {
        private boolean bareJid;

        public static Jid fromString(String jidStr) throws InvalidJidException {
            // Placeholder for JID parsing logic
            if (jidStr == null || jidStr.isEmpty()) throw new InvalidJidException();
            return new Jid();
        }

        public boolean isBareJid() {
            return bareJid;
        }

        public void setBareJid(boolean bareJid) {
            this.bareJid = bareJid;
        }

        @Override
        public String toString() {
            // Placeholder for JID string representation logic
            return "jid";
        }
    }

    static class InvalidJidException extends Exception {}

    interface Conversation {
        Bookmark getBookmark();
        Account getAccount();
        String getAttribute(String attributeName);
        Jid getJid();

        static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
    }

    static class Bookmark {
        private String nick;
        private String password;

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
}