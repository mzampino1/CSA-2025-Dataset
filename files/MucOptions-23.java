package com.example.xmpp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MucOptions {

    public enum Error {
        NONE,
        NO_RESPONSE,
        INVALID_NICKNAME,
        PASSWORD_REQUIRED,
        CONNECTION_ERROR
    }

    private final Account account;
    private final Conversation conversation;
    private boolean isOnline;
    private Error error = Error.NONE;
    private User self;

    public MucOptions(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
        this.self = new User(this, createJoinJid(getProposedNick()));
    }

    // ... other methods ...

    private String getProposedNick() {
        if (conversation.getBookmark() != null
                && conversation.getBookmark().getNick() != null
                && !conversation.getBookmark().getNick().trim().isEmpty()) {
            return conversation.getBookmark().getNick().trim();
        } else if (!conversation.getJid().isBareJid()) {
            return conversation.getJid().getResource();
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
        // Implementation here
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

    public boolean setSubject(String subject) {
        return this.conversation.setAttribute("subject", subject);
    }

    public String getSubject() {
        return this.conversation.getAttribute("subject");
    }

    public String getName() {
        return this.conversation.getAttribute("muc_name");
    }

    private List<User> getFallbackUsersFromCryptoTargets() {
        List<User> users = new ArrayList<>();
        for (Jid jid : conversation.getAcceptedCryptoTargets()) {
            User user = new User(this, null);
            user.setRealJid(jid);
            users.add(user);
        }
        return users;
    }

    public List<User> getUsersRelevantForNameAndAvatar() {
        final List<User> users;
        if (isOnline) {
            users = getUsers(5);
        } else {
            users = getFallbackUsersFromCryptoTargets();
        }
        return users;
    }

    public String createNameFromParticipants() {
        List<User> users = getUsersRelevantForNameAndAvatar();
        if (users.size() >= 2) {
            StringBuilder builder = new StringBuilder();
            for (User user : users) {
                if (builder.length() != 0) {
                    builder.append(", ");
                }
                String name = UIHelper.getDisplayName(user);
                if (name != null) {
                    builder.append(name.split("\\s+")[0]);
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

    // Potential security vulnerability: Nickname not validated or sanitized
    public Jid createJoinJid(String nick) {
        try {
            return Jid.of(this.conversation.getJid().asBareJid().toString() + "/" + nick);
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    public Jid getTrueCounterpart(Jid jid) {
        if (jid.equals(getSelf().getFullJid())) {
            return account.getJid().asBareJid();
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

    public List<Jid> getMembers(final boolean includeDomains) {
        ArrayList<Jid> members = new ArrayList<>();
        synchronized (users) {
            for (User user : users) {
                if (user.affiliation.ranks(Affiliation.MEMBER) && user.realJid != null && (!user.isDomain() || includeDomains)) {
                    members.add(user.realJid);
                }
            }
        }
        return members;
    }

    // ... other methods ...

    private final Set<User> users = new HashSet<>();

    public boolean setOnline() {
        boolean before = this.isOnline;
        this.isOnline = true;
        return !before;
    }

    public ArrayList<User> getUsers() {
        return getUsers(true);
    }

    public ArrayList<User> getUsers(boolean includeOffline) {
        synchronized (users) {
            ArrayList<User> users = new ArrayList<>();
            for (User user : this.users) {
                if (!user.isDomain() && (includeOffline || user.getRole().ranks(Role.PARTICIPANT))) {
                    users.add(user);
                }
            }
            return users;
        }
    }

    public ArrayList<User> getUsersWithChatState(ChatState state, int max) {
        synchronized (users) {
            ArrayList<User> list = new ArrayList<>();
            for (User user : users) {
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
        ArrayList<User> subset = new ArrayList<>();
        HashSet<Jid> jids = new HashSet<>();
        jids.add(account.getJid().asBareJid());
        synchronized (users) {
            for (User user : users) {
                if (user.getRealJid() == null || (user.getRealJid().getLocal() != null && jids.add(user.getRealJid()))) {
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

    private String password;

    public boolean isContactInRoom(Contact contact) {
        return findUserByRealJid(contact.getJid().asBareJid()) != null;
    }

    public boolean isUserInRoom(Jid jid) {
        return findUserByFullJid(jid) != null;
    }

    public void setError(Error error) {
        this.isOnline = isOnline && error == Error.NONE;
        this.error = error;
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

    public User findUserByRealJid(Jid jid) {
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

    public User findUser(ReadByMarker readByMarker) {
        if (readByMarker.getRealJid() != null) {
            User user = findUserByRealJid(readByMarker.getRealJid().asBareJid());
            if (user == null) {
                user = new User(this, readByMarker.getFullJid());
                user.setRealJid(readByMarker.getRealJid());
            }
            return user;
        } else if (readByMarker.getFullJid() != null) {
            return findUserByFullJid(readByMarker.getFullJid());
        }
        return null;
    }

    public boolean setErrorIfNotJoined() {
        // Implementation here
        return false;
    }

    private static class User {
        private final MucOptions mucOptions;
        private Jid fullJid;
        private Jid realJid;
        private String name;
        private long pgpKeyId = 0L;
        private Role role = Role.NONE;
        private Affiliation affiliation = Affiliation.NONE;
        private ChatState chatState = ChatState.GONE;

        public User(MucOptions mucOptions, Jid fullJid) {
            this.mucOptions = mucOptions;
            this.fullJid = fullJid;
        }

        public Jid getFullJid() {
            return fullJid;
        }

        public void setRealJid(Jid realJid) {
            this.realJid = realJid;
        }

        public Jid getRealJid() {
            return realJid;
        }

        public String getName() {
            return name;
        }

        public long getPgpKeyId() {
            return pgpKeyId;
        }

        public Role getRole() {
            return role;
        }

        public Affiliation getAffiliation() {
            return affiliation;
        }

        public ChatState getChatState() {
            return chatState;
        }
    }

    private enum Role {
        NONE,
        VISITOR,
        PARTICIPANT,
        MODERATOR
    }

    private enum Affiliation {
        NONE,
        MEMBER,
        ADMINISTRATOR,
        OWNER
    }

    public interface OnRenameListener {
        void onRename();
    }
}