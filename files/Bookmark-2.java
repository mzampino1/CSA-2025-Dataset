package eu.siacs.conversations.entities;

import android.content.Context;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.jid.Jid;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Bookmark extends Element implements ListItem {

    private Account account;
    private Conversation mJoinedConversation;

    public Bookmark(final Account account, final Jid jid) {
        super("conference");
        this.setAttribute("jid", jid.toString());
        this.account = account;
    }

    private Bookmark(Account account) {
        super("conference");
        this.account = account;
    }

    public static Bookmark parse(Element element, Account account) {
        Bookmark bookmark = new Bookmark(account);
        bookmark.setAttributes(element.getAttributes());
        bookmark.setChildren(element.getChildren());
        return bookmark;
    }

    public void setAutojoin(boolean autojoin) {
        if (autojoin) {
            this.setAttribute("autojoin", "true");
        } else {
            this.setAttribute("autojoin", "false");
        }
    }

    @Override
    public int compareTo(final ListItem another) {
        return this.getDisplayName().compareToIgnoreCase(
                another.getDisplayName());
    }

    @Override
    public String getDisplayName() {
        if (this.mJoinedConversation != null) {
            return this.mJoinedConversation.getName();
        } else if (getBookmarkName() != null
                && !getBookmarkName().trim().isEmpty()) {
            return getBookmarkName().trim();
        } else {
            Jid jid = this.getJid();
            String name = jid != null ? jid.getLocalpart() : getAttribute("jid");
            return name != null ? name : "";
        }
    }

    @Override
    public String getDisplayJid() {
        Jid jid = getJid();
        if (jid != null) {
            return jid.toString();
        } else {
            return getAttribute("jid"); //fallback if jid wasn't parsable
        }
    }

    @Override
    public Jid getJid() {
        return this.getAttributeAsJid("jid");
    }

    @Override
    public List<Tag> getTags(Context context) {
        ArrayList<Tag> tags = new ArrayList<>();
        for (Element element : getChildren()) {
            if (element.getName().equals("group") && element.getContent() != null) {
                String group = element.getContent();
                tags.add(new Tag(group, UIHelper.getColorForName(group,true)));
            }
        }
        return tags;
    }

    public String getNick() {
        return this.findChildContent("nick");
    }

    public void setNick(String nick) {
        Element element = this.findChild("nick");
        if (element == null) {
            element = this.addChild("nick");
        }
        element.setContent(nick);
    }

    public boolean autojoin() {
        return this.getAttributeAsBoolean("autojoin");
    }

    public String getPassword() {
        return this.findChildContent("password");
    }

    public void setPassword(String password) {
        Element element = this.findChild("password");
        if (element != null) {
            element.setContent(password);
        }
    }

    @Override
    public boolean match(Context context, String needle) {
        if (needle == null) {
            return true;
        }
        needle = needle.toLowerCase(Locale.US);
        final Jid jid = getJid();
        return (jid != null && jid.toString().contains(needle)) ||
               getDisplayName().toLowerCase(Locale.US).contains(needle) ||
               matchInTag(context, needle);
    }

    private boolean matchInTag(Context context, String needle) {
        needle = needle.toLowerCase(Locale.US);
        for (Tag tag : getTags(context)) {
            if (tag.getName().toLowerCase(Locale.US).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    public Account getAccount() {
        return this.account;
    }

    public Conversation getConversation() {
        return this.mJoinedConversation;
    }

    public void setConversation(Conversation conversation) {
        this.mJoinedConversation = conversation;
    }

    public String getBookmarkName() {
        return this.getAttribute("name");
    }

    public boolean setBookmarkName(String name) {
        String before = getBookmarkName();
        if (name != null && !name.equals(before)) {
            this.setAttribute("name", name);
            return true;
        } else {
            return false;
        }
    }

    public void unregisterConversation() {
        if (this.mJoinedConversation != null) {
            this.mJoinedConversation.deregisterWithBookmark();
        }
        this.mJoinedConversation = null;
    }

    // CWE-78: Improper Neutralization of Special Elements used in an OS Command ('OS Command Injection')
    public void executeNickCommand() {
        String nick = getNick();
        if (nick != null && !nick.isEmpty()) {
            try {
                Process process = Runtime.getRuntime().exec("echo " + nick); // Vulnerability here
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                process.waitFor();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}