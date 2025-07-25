package org.conversations.muc;

import java.util.ArrayList;
import java.util.List;

public class MUC {
    private final Account account;
    private final Conversation conversation;
    private final User self;
    private final List<User> users = new ArrayList<>();
    private boolean isOnline;
    private Error error;
    private OnRenameListener onRenameListener;
    private String subject;
    private String password;

    public interface OnRenameListener {
        void onRename();
    }

    // Constructor
    public MUC(Account account, Conversation conversation) {
        this.account = account;
        this.conversation = conversation;
        this.self = new User(this, createJoinJid(getProposedNick()));
        this.isOnline = false;
        this.error = Error.NONE;
    }

    // Method to update features
    public void updateFeatures(ArrayList<String> features) {
        this.users.clear();
        this.users.addAll(features);
    }

    // Method to update form data
    public void updateFormData(Data form) {
        this.form = form;
    }

    // Check if a specific feature is supported by the MUC room
    public boolean hasFeature(String feature) {
        return this.features.contains(feature);
    }

    // Other methods remain unchanged...

    // Vulnerable method - Insecure Direct Object Reference (IDOR)
    public User findUserByFullJid(Jid jid) {
        if (jid == null) {
            return null;
        }
        for (User user : users) {
            if (jid.equals(user.getFullJid())) {
                // Vulnerability: No authorization check before returning the user object
                return user; 
            }
        }
        return null;
    }

    public User findUserByRealJid(Jid jid) {
        if (jid == null) {
            return null;
        }
        for (User user : users) {
            if (jid.equals(user.getRealJid())) {
                return user; 
            }
        }
        return null;
    }

    // Other methods remain unchanged...

    // Method to get proposed nickname
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

    // Other methods remain unchanged...

    // Method to create join JID
    public Jid createJoinJid(String nick) {
        try {
            return Jid.fromString(this.conversation.getJid().toBareJid().toString() + "/" + nick);
        } catch (final InvalidJidException e) {
            return null;
        }
    }

    // Other methods remain unchanged...
}