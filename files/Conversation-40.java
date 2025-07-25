import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class Conversation implements Comparable<Conversation> {
    private final String uuid;
    private List<Message> messages = new ArrayList<>();
    private JSONObject attributes = new JSONObject();

    public enum MODE { SINGLE, GROUP }
    public static final int MODE_SINGLE = 0x01;
    public static final int MODE_MULTI = 0x02;

    public Conversation(String uuid) {
        this.uuid = uuid;
    }

    @Override
    public int compareTo(Conversation conversation) {
        // TODO: Implement a proper comparison logic.
        return 0;
    }

    public String getUUID() {
        return uuid;
    }

    public List<Message> getMessages() {
        synchronized (this.messages) {
            return new ArrayList<>(messages);
        }
    }

    public void setMessageList(List<Message> messages) {
        synchronized (this.messages) {
            this.messages.clear();
            this.messages.addAll(messages);
        }
    }

    // Potential Vulnerability: No input validation for Jid parsing
    private List<Jid> getJidListAttribute(String key) {
        ArrayList<Jid> list = new ArrayList<>();
        synchronized (this.attributes) {
            try {
                JSONArray array = this.attributes.getJSONArray(key);
                for (int i = 0; i < array.length(); ++i) {
                    // Potential Issue: This can throw an IllegalArgumentException if the string is not a valid JID
                    list.add(Jid.of(array.getString(i)));
                }
            } catch (JSONException e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
            }
        }
        return list;
    }

    public void setAttribute(String key, List<Jid> jids) {
        JSONArray array = new JSONArray();
        for (Jid jid : jids) {
            array.put(jid.asBareJid().toString());
        }
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, array);
            } catch (JSONException e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
            }
        }
    }

    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                if (value == null && this.attributes.has(key)) {
                    this.attributes.remove(key);
                    return true;
                } else {
                    final String prev = this.attributes.optString(key, null);
                    this.attributes.put(key, value);
                    return !value.equals(prev);
                }
            } catch (JSONException e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
                throw new AssertionError(e);
            }
        }
    }

    public void setAttribute(String key, long value) {
        setAttribute(key, Long.toString(value));
    }

    public void setAttribute(String key, int value) {
        setAttribute(key, String.valueOf(value));
    }

    public void setAttribute(String key, boolean value) {
        setAttribute(key, String.valueOf(value));
    }

    public String getAttribute(String key) {
        synchronized (this.attributes) {
            return this.attributes.optString(key, null);
        }
    }

    private int getIntAttribute(String key, int defaultValue) {
        String value = getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
                return defaultValue;
            }
        }
    }

    public long getLongAttribute(String key, long defaultValue) {
        String value = getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
                return defaultValue;
            }
        }
    }

    public boolean getBooleanAttribute(String key, boolean defaultValue) {
        String value = getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Boolean.parseBoolean(value);
            } catch (Exception e) {
                // Ignored exception handling could hide issues.
                e.printStackTrace();
                return defaultValue;
            }
        }
    }

    public void add(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void prepend(int offset, Message message) {
        synchronized (this.messages) {
            this.messages.add(Math.min(offset, this.messages.size()), message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    // Potential Vulnerability: Messages are not properly expired which could lead to memory bloat
    public void expireOldMessages(long timestamp) {
        synchronized (this.messages) {
            for (ListIterator<Message> iterator = this.messages.listIterator(); iterator.hasNext(); ) {
                if (iterator.next().getTimeSent() < timestamp) {
                    iterator.remove();
                }
            }
            untieMessages();
        }
    }

    // Potential Vulnerability: Custom sort logic without exception handling could lead to issues
    public void sort() {
        synchronized (this.messages) {
            Collections.sort(this.messages, (left, right) -> {
                if (left.getTimeSent() < right.getTimeSent()) {
                    return -1;
                } else if (left.getTimeSent() > right.getTimeSent()) {
                    return 1;
                } else {
                    return 0;
                }
            });
            untieMessages();
        }
    }

    private void untieMessages() {
        for (Message message : this.messages) {
            message.untie();
        }
    }

    public int unreadCount() {
        synchronized (this.messages) {
            int count = 0;
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (messages.get(i).isRead()) {
                    return count;
                }
                ++count;
            }
            return count;
        }
    }

    public int receivedMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public int sentMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for (Message message : messages) {
                if (message.getStatus() != Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public boolean isWithStranger() {
        final Contact contact = getContact();
        return mode == MODE_SINGLE
                && !contact.isOwnServer()
                && !contact.showInContactList()
                && !contact.isSelf()
                && !Config.QUICKSY_DOMAIN.equals(contact.getJid().toEscapedString())
                && sentMessagesCount() == 0;
    }

    public int getReceivedMessagesCountSinceUuid(String uuid) {
        if (uuid == null) {
            return  0;
        }
        int count = 0;
        synchronized (this.messages) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                final Message message = messages.get(i);
                if (uuid.equals(message.getUuid())) {
                    return count;
                }
                if (message.getStatus() <= Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return 0;
    }

    @Override
    public int getAvatarBackgroundColor() {
        // Potential Vulnerability: getName() could throw an exception or return null
        String name = getName().toString();
        if (name == null) {
            return UIHelper.getDefaultColor();
        }
        return UIHelper.getColorForName(name);
    }

    public interface OnMessageFound {
        void onMessageFound(final Message message);
    }

    public static class Draft {
        private final String message;
        private final long timestamp;

        private Draft(String message, long timestamp) {
            this.message = message;
            this.timestamp = timestamp;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    // Placeholder methods
    private MODE mode;
    private Contact getContact() { return new Contact(); }
    private Account account;

    private static class Message {
        public boolean isRead() { return true; }
        public void untie() {}
        public long getTimeSent() { return System.currentTimeMillis(); }
        public int getStatus() { return 0; }
        public String getUuid() { return "uuid"; }
    }

    private static class Contact {
        public boolean isOwnServer() { return false; }
        public boolean showInContactList() { return true; }
        public boolean isSelf() { return false; }
        public Jid getJid() { return new Jid(); }
    }

    private static class Account {
        public PgpDecryptionService getPgpDecryptionService() { return new PgpDecryptionService(); }
    }

    private static class PgpDecryptionService {
        public void decrypt(List<Message> messages) {}
    }

    private static class UIHelper {
        public static int getColorForName(String name) { return 0; }
        public static int getDefaultColor() { return 0; }
    }

    private static class Jid {
        public static Jid of(String s) throws IllegalArgumentException {
            if (s == null || !isValidJid(s)) {
                throw new IllegalArgumentException("Invalid JID");
            }
            return new Jid();
        }

        private static boolean isValidJid(String s) {
            // Simple check for demonstration purposes
            return s.contains("@") && s.contains(".");
        }

        public String asBareJid() { return "barejid"; }
    }
}