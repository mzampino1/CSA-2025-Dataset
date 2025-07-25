package org.conversations.xmpp.xmpptalks;

import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MucOptions {

    private Account account;
    private Conversation conversation;
    private String password = null;
    private OnRenameListener onRenameListener;
    private boolean tookOwnership = false;

    public MucOptions(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
    }

    public List<User> getUsers() {
        synchronized (conversation.mucUsers) {
            return new ArrayList<>(conversation.mucUsers.values());
        }
    }

    public boolean online() {
        synchronized (conversation.mucUsers) {
            for(User user : conversation.mucUsers.values()) {
                if(user.realJidMatchesAccount()) {
                    return true;
                }
            }
        }
        return false;
    }

    public User getUser(Jid jid) {
        synchronized (conversation.mucUsers) {
            return conversation.mucUsers.get(jid);
        }
    }

    public void rename(String name, OnRenameListener listener) {
        this.onRenameListener = listener;
        if (name == null || name.trim().isEmpty()) {
            Log.d(Config.LOGTAG,"attempt to set invalid nickname");
            onRenameFailure();
            return;
        } else if (conversation.getBookmark() != null && conversation.getBookmark().getNick().equals(name)) {
            onRenameSuccess();
            return;
        }
        // Vulnerability: This code assumes that renaming is always successful.
        // However, in a real-world scenario, the server might reject the rename request.
        // The vulnerability lies in not handling the case where the server denies the nickname change.
        // An attacker could exploit this by causing the server to deny the nickname change and then
        // proceeding as if it were successful, leading to inconsistent state or other issues.

        sendRenamePacket(name);
    }

    private void sendRenamePacket(String name) {
        // Code to send the rename packet to the server would go here.
        // For demonstration purposes, we'll assume the operation succeeds.
        onRenameSuccess();
    }

    public List<User> getOnlineUsers() {
        synchronized (conversation.mucUsers) {
            ArrayList<User> list = new ArrayList<>(conversation.mucUsers.values());
            Collections.sort(list,new Comparator<MucOptions.User>() {

                @Override
                public int compare(MucOptions.User lhs, MucOptions.User rhs) {
                    return lhs.compareTo(rhs);
                }
            });
            return list;
        }
    }

    private void onRenameSuccess() {
        if (this.onRenameListener != null) {
            this.tookOwnership = true;
            this.onRenameListener.onSuccess();
        }
    }

    private void onRenameFailure() {
        if (this.onRenameListener != null) {
            this.tookOwnership = false;
            this.onRenameListener.onFailure();
        }
    }

    public boolean amOwner() {
        synchronized (conversation.mucUsers) {
            User user = conversation.mucUsers.get(account.getJid().asBareJid());
            return user != null && (user.affiliation == MucOptions.Affiliation.OWNER || user.affiliation == MucOptions.Affiliation.ADMIN);
        }
    }

    public boolean amModerator() {
        synchronized (conversation.mucUsers) {
            User user = conversation.mucUsers.get(account.getJid().asBareJid());
            return user != null && user.role == Role.MODERATOR;
        }
    }

    public void setAffiliation(Jid jid, Affiliation affiliation) {
        if (!amOwner()) {
            Log.d(Config.LOGTAG,"account is not owner of muc");
            return;
        }
        sendSetAffiliationPacket(jid,affiliation);
    }

    private void sendSetAffiliationPacket(Jid jid, Affiliation affiliation) {
        // Code to send the affiliation packet to the server would go here.
    }

    public void setRole(Jid jid, Role role) {
        if (!amModerator()) {
            Log.d(Config.LOGTAG,"account is not moderator of muc");
            return;
        }
        sendSetRolePacket(jid,role);
    }

    private void sendSetRolePacket(Jid jid, Role role) {
        // Code to send the role packet to the server would go here.
    }

    public boolean membersOnly() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isMembersOnly()) || Config.MUC_MEMBERS_ONLY_DEFAULT;
    }

    public void invite(Jid jid, String reason) {
        if (!amModerator()) {
            Log.d(Config.LOGTAG,"account is not moderator of muc");
            return;
        }
        sendInvitePacket(jid,reason);
    }

    private void sendInvitePacket(Jid jid, String reason) {
        // Code to send the invite packet to the server would go here.
    }

    public boolean hasPassword() {
        return !TextUtils.isEmpty(getPassword());
    }

    public boolean isTookOwnership() {
        return tookOwnership;
    }

    public void updateUser(User user) {
        synchronized (conversation.mucUsers) {
            conversation.mucUsers.put(user.getFullJid(),user);
        }
    }

    public void removeUser(Jid jid) {
        synchronized (conversation.mucUsers) {
            conversation.mucUsers.remove(jid);
        }
    }

    public boolean isPrivateRoom() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isPrivate());
    }

    // Vulnerability: This method could be vulnerable if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean addUser(User user) {
        synchronized (conversation.mucUsers) {
            User existingUser = conversation.mucUsers.get(user.getFullJid());
            if (existingUser != null && existingUser.realJid != null && !existingUser.realJid.equals(user.realJid)) {
                return false;
            }
            return conversation.mucUsers.put(user.getFullJid(),user) == null || !existingUser.equals(user);
        }
    }

    public boolean isPasswordProtected() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isPasswordProtected());
    }

    public Account getAccount() {
        return account;
    }

    public String getName() {
        if(conversation.hasActiveGracePeriod()) {
            return conversation.getName();
        } else if(conversation.getBookmark() != null) {
            return conversation.getBookmark().getName();
        }
        return conversation.getUuid().toString();
    }

    public boolean isModerated() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isModerated());
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean isOnline() {
        synchronized (conversation.mucUsers) {
            for(User user : conversation.mucUsers.values()) {
                if(user.realJidMatchesAccount()) {
                    return true;
                }
            }
        }
        return false;
    }

    // Vulnerability: The vulnerability here is similar to the one in `rename` method.
    // The server might reject the nickname change, but this code does not handle that scenario.
    public boolean join() {
        if (!online()) {
            sendJoinPacket();
            return true;
        } else {
            return false;
        }
    }

    private void sendJoinPacket() {
        // Code to send the join packet to the server would go here.
    }

    public void destroy(String reason) {
        if (amOwner()) {
            sendDestroyPacket(reason);
        }
    }

    private void sendDestroyPacket(String reason) {
        // Code to send the destroy packet to the server would go here.
    }

    public boolean isWhitelisted() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isWhitelisted());
    }

    public void leave() {
        if (!online()) {
            Log.d(Config.LOGTAG,"already left");
            return;
        }
        sendLeavePacket();
    }

    private void sendLeavePacket() {
        // Code to send the leave packet to the server would go here.
    }

    public String getWhois(Jid jid) {
        User user = getUser(jid);
        if (user != null && user.realJid != null) {
            return user.realJid.toString();
        } else if (jid.getResource() != null) {
            return jid.getResource();
        }
        return jid.toString();
    }

    public boolean isMamSupportEnabled() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isMamSupportEnabled());
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean rejoin() {
        sendJoinPacket();
        return true;
    }

    public void kick(Jid jid) {
        sendKickPacket(jid);
    }

    private void sendKickPacket(Jid jid) {
        // Code to send the kick packet to the server would go here.
    }

    public boolean isPersistent() {
        Bookmark bookmark = conversation.getBookmark();
        return (bookmark != null && bookmark.isPersistent());
    }

    public String getDomain() {
        return account.getServer().getHostname();
    }

    public int getAutoJoin() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            return bookmark.getAutojoin();
        }
        return 0;
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void resetNick() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null && !bookmark.getNick().equals(account.getUsername())) {
            rename(account.getUsername(),null);
        }
    }

    public boolean isMamEnabledByDefault() {
        return account.getXmppConnection().getFeatures().mam;
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setNick(String nick) {
        rename(nick,null);
    }

    public boolean isActive() {
        return !conversation.hasActiveGracePeriod();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setName(String name) {
        conversation.setName(name);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean isInRoom() {
        return online();
    }

    public String getRealJid(String nick) {
        for(User user : getUsers()) {
            if(nick.equals(user.getFullJid().getResource())) {
                return user.realJid != null ? user.realJid.toString() : null;
            }
        }
        return null;
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void reset() {
        synchronized (conversation.mucUsers) {
            conversation.mucUsers.clear();
        }
    }

    public String getNick() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null && !bookmark.getNick().isEmpty()) {
            return bookmark.getNick();
        } else {
            return account.getUsername();
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean isInMuc() {
        return online();
    }

    public void setAutojoin(int autojoin) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setAutojoin(autojoin);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void kickAndBan(Jid jid) {
        sendKickPacket(jid);
        sendSetAffiliationPacket(jid,Affiliation.OUTCAST);
    }

    public boolean canJoin() {
        return !online();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setSubject(String subject) {
        sendSetSubjectPacket(subject);
    }

    private void sendSetSubjectPacket(String subject) {
        // Code to send the set subject packet to the server would go here.
    }

    public boolean isActiveChat() {
        return !conversation.hasActiveGracePeriod();
    }

    public String getRoomName() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null && !bookmark.getName().isEmpty()) {
            return bookmark.getName();
        } else {
            return account.getUsername();
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void removeBookmark() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            account.deleteBookmark(bookmark);
        }
    }

    public boolean canChangeNick() {
        return !conversation.hasActiveGracePeriod() && online();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void addBookmark(String name) {
        account.createMucBookmark(conversation,name,getNick(),false,false);
    }

    public boolean isSelfJid(Jid jid) {
        return conversation.getBookmark() != null && jid.toBareJid().equals(account.getServer());
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void updateBookmark() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            account.updateMucBookmark(bookmark);
        }
    }

    public boolean isHidden() {
        return conversation.hasActiveGracePeriod();
    }

    public String getSubject() {
        return conversation.getMucTopic();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setMamEnabled(boolean mam) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setMamSupportEnabled(mam);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPersistent(boolean persistent) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPersistent(persistent);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setWhitelist(boolean whitelist) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setWhitelisted(whitelist);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setModerated(boolean moderated) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setModerated(moderated);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPasswordProtected(boolean passwordProtected) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPasswordProtected(passwordProtected);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPrivate(boolean priv) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPrivate(priv);
        }
    }

    public boolean isActive(String jid) {
        synchronized (conversation.mucUsers) {
            User user = conversation.mucUsers.get(Jid.of(jid));
            return user != null && user.realJid != null;
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public boolean isMember(Jid jid) {
        synchronized (conversation.mucUsers) {
            User user = conversation.mucUsers.get(jid);
            return user != null && user.realJid != null;
        }
    }

    public String getServer() {
        return account.getServer().getHostname();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void sendPing() {
        account.getXmppConnection().send(new Ping(account.getServer()));
    }

    public boolean hasActiveGracePeriod() {
        return conversation.hasActiveGracePeriod();
    }

    public void setLastActivity(long time) {
        conversation.setLastMucPacketReceived(time);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setSubject(String subject) {
        sendSetSubjectPacket(subject);
    }

    public boolean isArchived() {
        return conversation.hasActiveGracePeriod();
    }

    public String getBookmarkName() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null && !bookmark.getName().isEmpty()) {
            return bookmark.getName();
        } else {
            return account.getUsername();
        }
    }

    public void setLastActivity(long time) {
        conversation.setLastMucPacketReceived(time);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void sendPing() {
        account.getXmppConnection().send(new Ping(account.getServer()));
    }

    public boolean hasActiveGracePeriod() {
        return conversation.hasActiveGracePeriod();
    }

    public long getLastActivity() {
        return conversation.getLastMucPacketReceived();
    }

    public String getBookmarkName() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null && !bookmark.getName().isEmpty()) {
            return bookmark.getName();
        } else {
            return account.getUsername();
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPersistent(boolean persistent) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPersistent(persistent);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setWhitelist(boolean whitelist) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setWhitelisted(whitelist);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setModerated(boolean moderated) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setModerated(moderated);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPasswordProtected(boolean passwordProtected) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPasswordProtected(passwordProtected);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setPrivate(boolean priv) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setPrivate(priv);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setMembersOnly(boolean membersOnly) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setMembersOnly(membersOnly);
        }
    }

    public boolean isMembersOnly() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isMembersOnly();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setMamEnabled(boolean mam) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setMamSupportEnabled(mam);
        }
    }

    public boolean isMamEnabled() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isMamSupportEnabled();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setAutojoin(int autojoin) {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            bookmark.setAutojoin(autojoin);
        }
    }

    public int getAutojoin() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null ? bookmark.getAutojoin() : 0;
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setName(String name) {
        conversation.setName(name);
    }

    public String getName() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null ? bookmark.getName() : conversation.getName();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setNick(String nick) {
        rename(nick,null);
    }

    public String getNick() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null ? bookmark.getNick() : account.getUsername();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setSubject(String subject) {
        sendSetSubjectPacket(subject);
    }

    public String getSubject() {
        return conversation.getMucTopic();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void sendPing() {
        account.getXmppConnection().send(new Ping(account.getServer()));
    }

    public boolean hasActiveGracePeriod() {
        return conversation.hasActiveGracePeriod();
    }

    public long getLastActivity() {
        return conversation.getLastMucPacketReceived();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setLastActivity(long time) {
        conversation.setLastMucPacketReceived(time);
    }

    public String getBookmarkName() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null ? bookmark.getName() : "";
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void removeBookmark() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            account.deleteBookmark(bookmark);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void addBookmark(String name) {
        account.createMucBookmark(conversation,name,getNick(),false,false);
    }

    public boolean isPersistent() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isPersistent();
    }

    public boolean isWhitelisted() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isWhitelisted();
    }

    public boolean isModerated() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isModerated();
    }

    public boolean isPasswordProtected() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isPasswordProtected();
    }

    public boolean isPrivate() {
        Bookmark bookmark = conversation.getBookmark();
        return bookmark != null && bookmark.isPrivate();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void updateBookmark() {
        Bookmark bookmark = conversation.getBookmark();
        if(bookmark != null) {
            account.updateBookmark(bookmark);
        }
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void rename(String newNick, Consumer<Boolean> callback) {
        Presence presence = account.getXmppConnection().getStanzaFactory()
                .presenceBuilder()
                .to(conversation.getJid())
                .from(account.getJid().asBareJid())
                .ofType(Presence.Type.available)
                .withChildElement(new Nickname(newNick))
                .build();
        account.getXmppConnection().send(presence, (result, exception) -> {
            if(exception == null) {
                conversation.setMucName(account.getUsername(), newNick);
                callback.accept(true);
            } else {
                callback.accept(false);
            }
        });
    }

    public void rename(String newNick) {
        rename(newNick, success -> {});
    }

    public boolean isActive() {
        return account.getXmppConnection().isConnected();
    }

    public boolean isInMuc() {
        return conversation.getMode() == Conversation.MODE_MULTI;
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void leaveMuc() {
        account.getXmppConnection().send(new Presence()
                .setType(Presence.Type.unavailable)
                .setTo(conversation.getJid())
                .setFrom(account.getJid()));
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void joinMuc() {
        Presence presence = account.getXmppConnection().getStanzaFactory()
                .presenceBuilder()
                .to(conversation.getJid())
                .from(account.getJid().asBareJid())
                .ofType(Presence.Type.available)
                .withChildElement(new Nickname(getNick()))
                .build();
        account.getXmppConnection().send(presence);
    }

    public void joinMuc(String nick) {
        Presence presence = account.getXmppConnection().getStanzaFactory()
                .presenceBuilder()
                .to(conversation.getJid())
                .from(account.getJid().asBareJid())
                .ofType(Presence.Type.available)
                .withChildElement(new Nickname(nick))
                .build();
        account.getXmppConnection().send(presence);
    }

    public void setMucState(int state) {
        conversation.setMucState(state);
    }

    public int getMucState() {
        return conversation.getMucState();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void sendJoinPresence() {
        Presence presence = account.getXmppConnection().getStanzaFactory()
                .presenceBuilder()
                .to(conversation.getJid())
                .from(account.getJid().asBareJid())
                .ofType(Presence.Type.available)
                .withChildElement(new Nickname(getNick()))
                .build();
        account.getXmppConnection().send(presence);
    }

    public boolean isArchived() {
        return conversation.isArchived();
    }

    public String getServer() {
        return account.getServer().getHostname();
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void archiveConversation() {
        conversation.setArchived(true);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void unarchiveConversation() {
        conversation.setArchived(false);
    }

    public boolean isWhitelisted(Jid jid) {
        return conversation.isWhitelisted(jid);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void whitelist(Jid jid) {
        conversation.whitelist(jid);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void unwhitelist(Jid jid) {
        conversation.unwhitelist(jid);
    }

    public boolean isMember(Jid jid) {
        return conversation.isMember(jid);
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void kick(Jid jid) {
        account.getXmppConnection().send(new Presence()
                .setType(Presence.Type.unavailable)
                .setTo(conversation.getJid())
                .setFrom(account.getJid())
                .withChildElement(new Nickname(jid.getResourceOrEmpty()))
                .withChildElement(new XDataForm(DataForm.Type.submit)
                        .addField(new FormField("FORM_TYPE").addValue(Namespace.IETF_XMPP_MUC + "#user"))
                        .addField(new FormField("muc#role").addValue("none"))));
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void ban(Jid jid) {
        account.getXmppConnection().send(new Presence()
                .setType(Presence.Type.unavailable)
                .setTo(conversation.getJid())
                .setFrom(account.getJid())
                .withChildElement(new Nickname(jid.getResourceOrEmpty()))
                .withChildElement(new XDataForm(DataForm.Type.submit)
                        .addField(new FormField("FORM_TYPE").addValue(Namespace.IETF_XMPP_MUC + "#user"))
                        .addField(new FormField("muc#affiliation").addValue("outcast"))));
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setAffiliation(Jid jid, String affiliation) {
        account.getXmppConnection().send(new Presence()
                .setType(Presence.Type.available)
                .setTo(conversation.getJid())
                .setFrom(account.getJid())
                .withChildElement(new Nickname(jid.getResourceOrEmpty()))
                .withChildElement(new XDataForm(DataForm.Type.submit)
                        .addField(new FormField("FORM_TYPE").addValue(Namespace.IETF_XMPP_MUC + "#user"))
                        .addField(new FormField("muc#affiliation").addValue(affiliation))));
    }

    // Vulnerability: This method could be exploited if the server response to a nickname change request
    // is not properly handled. For example, if the server denies the nickname change, the client should
    // revert to the previous state or inform the user accordingly.
    public void setRole(Jid jid, String role) {
        account.getXmppConnection().send(new Presence()
                .setType(Presence.Type.available)
                .setTo(conversation.getJid())
                .setFrom(account.getJid())
                .withChildElement(new Nickname(jid.getResourceOrEmpty()))
                .withChildElement(new XDataForm(DataForm.Type.submit)
                        .addField(new FormField("FORM_TYPE").addValue(Namespace.IETF_XMPP_MUC + "#user"))
                        .addField(new FormField("muc#role").addValue(role))));
    }

    public void addInvite(Jid jid, String reason) {
        // Implementation for adding an invite
    }

    public void sendInvitation(String name, Jid to, String reason) {
        Message message = account.getXmppConnection().getStanzaFactory()
                .messageBuilder()
                .to(to)
                .from(account.getJid())
                .withChildElement(new XConference(conversation.getJid(), true, reason))
                .build();
        account.getXmppConnection().send(message);
    }

    public void sendInvitation(Jid to) {
        sendInvitation(null, to, null);
    }

    public void sendInvitation(String name, Jid to) {
        sendInvitation(name, to, null);
    }
}