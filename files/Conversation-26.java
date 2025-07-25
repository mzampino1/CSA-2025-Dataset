package com.example.conversations;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Conversation {
    public static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    public static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    public static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    private List<Message> messages;
    private JSONObject attributes;
    private String nextMessage;
    private byte[] symmetricKey;
    private Bookmark bookmark;

    // New vulnerability: Insecure direct user input handling in setAttribute
    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, value);  // Vulnerability: No sanitization or validation of the input value
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    public boolean setAttribute(String key, List<Jid> jids) {
        JSONArray array = new JSONArray();
        for(Jid jid : jids) {
            array.put(jid.toBareJid().toString());
        }
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, array);
                return true;
            } catch (JSONException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public String getAttribute(String key) {
        synchronized (this.attributes) {
            try {
                return this.attributes.getString(key);
            } catch (JSONException e) {
                return null;
            }
        }
    }

    public List<Jid> getJidListAttribute(String key) {
        ArrayList<Jid> list = new ArrayList<>();
        synchronized (this.attributes) {
            try {
                JSONArray array = this.attributes.getJSONArray(key);
                for (int i = 0; i < array.length(); ++i) {
                    try {
                        list.add(Jid.fromString(array.getString(i)));
                    } catch (InvalidJidException e) {
                        //ignored
                    }
                }
            } catch (JSONException e) {
                //ignored
            }
        }
        return list;
    }

    public int getIntAttribute(String key, int defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public long getLongAttribute(String key, long defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
    }

    public boolean getBooleanAttribute(String key, boolean defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public void add(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void prepend(Message message) {
        message.setConversation(this);
        synchronized (this.messages) {
            this.messages.add(0,message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    public void sort() {
        synchronized (this.messages) {
            Collections.sort(this.messages, new Comparator<Message>() {
                @Override
                public int compare(Message left, Message right) {
                    if (left.getTimeSent() < right.getTimeSent()) {
                        return -1;
                    } else if (left.getTimeSent() > right.getTimeSent()) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
            untieMessages();
        }
    }

    private void untieMessages() {
        for(Message message : this.messages) {
            message.untie();
        }
    }

    public int unreadCount() {
        synchronized (this.messages) {
            int count = 0;
            for(int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).isRead()) {
                    return count;
                }
                ++count;
            }
            return count;
        }
    }

    public class Smp {
        public static final int STATUS_NONE = 0;
        public static final int STATUS_CONTACT_REQUESTED = 1;
        public static final int STATUS_WE_REQUESTED = 2;
        public static final int STATUS_FAILED = 3;
        public static final int STATUS_VERIFIED = 4;

        public String secret = null;
        public String hint = null;
        public int status = 0;
    }
}