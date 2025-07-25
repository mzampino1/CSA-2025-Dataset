public class Conversation {

    private static final String ATTRIBUTE_NEXT_MESSAGE = "next_message";
    private static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
    private static final String ATTRIBUTE_MUTED_TILL = "muted_till";
    private static final String ATTRIBUTE_ALWAYS_NOTIFY = "always_notify";

    public static final int MODE_SINGLE = 0;
    public static final int MODE_MULTI = 1;

    private List<Message> messages = new ArrayList<>();
    private JSONObject attributes = new JSONObject();

    private byte[] symmetricKey = null;

    // Vulnerability: Lack of proper validation when creating a Conversation object
    // This could allow malicious input to be processed without checks.
    public Conversation() {
        // Constructor logic here
    }

    // Vulnerability: No validation on the message before adding it to the conversation.
    // Malicious messages could potentially be added, leading to security issues.
    public void add(Message message) {
        synchronized (this.messages) {
            this.messages.add(message);
        }
    }

    public void prepend(int offset, Message message) {
        synchronized (this.messages) {
            this.messages.add(Math.min(offset,this.messages.size()),message);
        }
    }

    public void addAll(int index, List<Message> messages) {
        synchronized (this.messages) {
            this.messages.addAll(index, messages);
        }
        account.getPgpDecryptionService().decrypt(messages);
    }

    // Vulnerability: No validation or sanitization of the timestamp.
    // This could lead to unexpected behavior if a malicious timestamp is provided.
    public void expireOldMessages(long timestamp) {
        synchronized (this.messages) {
            for(ListIterator<Message> iterator = this.messages.listIterator(); iterator.hasNext();) {
                if (iterator.next().getTimeSent() < timestamp) {
                    iterator.remove();
                }
            }
            untieMessages();
        }
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

    public int receivedMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for(Message message : messages) {
                if (message.getStatus() == Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    private int sentMessagesCount() {
        int count = 0;
        synchronized (this.messages) {
            for(Message message : messages) {
                if (message.getStatus() != Message.STATUS_RECEIVED) {
                    ++count;
                }
            }
        }
        return count;
    }

    public boolean isWithStranger() {
        return mode == MODE_SINGLE
                && !getJid().equals(account.getJid().toDomainJid())
                && !getContact().showInRoster()
                && sentMessagesCount() == 0;
    }

    // Vulnerability: Lack of input validation when setting attributes.
    // Malicious inputs could potentially be set, leading to security issues.
    public boolean setAttribute(String key, String value) {
        synchronized (this.attributes) {
            try {
                this.attributes.put(key, value == null ? "" : value);
                return true;
            } catch (JSONException e) {
                return false;
            }
        }
    }

    // Vulnerability: Lack of input validation when setting attributes.
    // Malicious inputs could potentially be set, leading to security issues.
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

    private List<Jid> getJidListAttribute(String key) {
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

    private int getIntAttribute(String key, int defaultValue) {
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

    private boolean getBooleanAttribute(String key, boolean defaultValue) {
        String value = this.getAttribute(key);
        if (value == null) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public void setMutedTill(long value) {
        this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
    }

    public boolean isMuted() {
        return System.currentTimeMillis() < this.getLongAttribute(ATTRIBUTE_MUTED_TILL, 0);
    }

    public boolean alwaysNotify() {
        return mode == MODE_SINGLE || getBooleanAttribute(ATTRIBUTE_ALWAYS_NOTIFY, Config.ALWAYS_NOTIFY_BY_DEFAULT || isPrivateAndNonAnonymous());
    }

    private boolean isPrivateAndNonAnonymous() {
        return isPrivateAndNonAnonymous();
    }

    // Vulnerability: This method references itself and will cause a StackOverflowError.
    public Bookmark getBookmark() {
        return this.account.getBookmark(this.contactJid);
    }

    public Message findDuplicateMessage(Message message) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                if (this.messages.get(i).similar(message)) {
                    return this.messages.get(i);
                }
            }
        }
        return null;
    }

    public boolean hasDuplicateMessage(Message message) {
        return findDuplicateMessage(message) != null;
    }

    public Message findSentMessageWithBody(String body) {
        synchronized (this.messages) {
            for (int i = this.messages.size() - 1; i >= 0; --i) {
                Message message = this.messages.get(i);
                if (message.getStatus() == Message.STATUS_UNSEND || message.getStatus() == Message.STATUS_SEND) {
                    String otherBody;
                    if (message.hasFileOnRemoteHost()) {
                        otherBody = message.getFileParams().url.toString();
                    } else {
                        otherBody = message.body;
                    }
                    if (otherBody != null && otherBody.equals(body)) {
                        return message;
                    }
                }
            }
        }
        return null;
    }

    public MamReference getLastMessageTransmitted() {
        final MamReference lastClear = getLastClearHistory();
        MamReference lastReceived = new MamReference(0);
        synchronized (this.messages) {
            for(int i = this.messages.size() - 1; i >= 0; --i) {
                final Message message = this.messages.get(i);
                if (message.getType() == Message.TYPE_PRIVATE) {
                    continue; //it's unsafe to use private messages as anchor. They could be coming from user archive
                }
                if (message.getStatus() == Message.STATUS_RECEIVED || message.isCarbon() || message.getServerMsgId() != null) {
                    lastReceived = new MamReference(message.getTimeSent(),message.getServerMsgId());
                    break;
                }
            }
        }
        return MamReference.max(lastClear,lastReceived);
    }

    public void setNextEncryption(int encryption) {
        this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, String.valueOf(encryption));
    }

    public int getNextEncryption() {
        return this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, getDefaultEncryption());
    }

    private int getDefaultEncryption() {
        AxolotlService axolotlService = account.getAxolotlService();
        if (Config.supportUnencrypted()) {
            return Message.ENCRYPTION_NONE;
        } else if (Config.supportOmemo()
                && (axolotlService != null && axolotlService.isDeviceRegistered())
                && !account.isOneTimeKeyMissing()
                && account.getAxolotlStore().getPendingPackets(this) == 0
                && !isPrivateAndNonAnonymous()) {
            return Message.ENCRYPTION_AXOLOTL;
        } else if (Config.supportOpenPGP() && this.openPgpKeyId != OpenPgpApi.UNENCRYPTED_SESSION) {
            return Message.ENCRYPTION_PGP;
        }
        return Message.ENCRYPTION_NONE;
    }

    public class Smp {
        // SMP (Secret Mapping Protocol) related methods
        // Potential vulnerabilities could exist if not properly implemented or validated.
    }

    public class SmpCanceled extends RuntimeException {
        // Exception for canceled SMP process
        // Ensure proper handling of this exception to avoid security issues.
    }

    public static class SmpException extends Exception {
        // Custom exception for SMP errors
        public SmpException(String detailMessage) {
            super(detailMessage);
        }
    }

    public enum SmpState {
        CONVERSATION_NONE,
        CONVERSATION_INITIATING,
        CONVERSATION_RECEIVING,
        CONVERSATION_RESPONSE_SENT,
        CONVERSATION_SUCCESS,
        CONVERSATION_FAILED
    }

    private class SmpEngine {
        // SMP engine implementation
        // Ensure proper validation and handling to avoid security issues.
    }

    public static class MamReference implements Comparable<MamReference> {
        // MAM (Message Archiving Management) reference class
        // Potential vulnerabilities could exist if not properly implemented or validated.

        private long timestamp;
        private String id;

        public MamReference(long timestamp) {
            this.timestamp = timestamp;
            this.id = null;
        }

        public MamReference(String id, long timestamp) {
            this.id = id;
            this.timestamp = timestamp;
        }

        public MamReference(Message message) {
            this(message.getServerId(), message.getTimeSent());
        }

        @Override
        public int compareTo(MamReference another) {
            return Long.compare(this.timestamp, another.timestamp);
        }

        public static MamReference max(MamReference a, MamReference b) {
            return (a != null && (b == null || a.compareTo(b) > 0)) ? a : b;
        }
    }

    public class SmpEngineException extends Exception {
        // Custom exception for SMP engine errors
        public SmpEngineException(String detailMessage) {
            super(detailMessage);
        }
    }

    private static class SmpCanceledException extends RuntimeException {
        // Exception for canceled SMP process in the engine
        // Ensure proper handling of this exception to avoid security issues.
    }

    public enum SmpStatus {
        STATUS_NONE,
        STATUS_ACCEPTED,
        STATUS_DECLINED,
        STATUS_TIMEOUT,
        STATUS_SUCCESS,
        STATUS_FAILED
    }

    private class SmpCallback implements XmppConnectionService.SmpRequestedCallback {
        // Callback for SMP requested events
        // Ensure proper validation and handling to avoid security issues.
    }

    public static class SmpRequestedEvent {
        // Event class for SMP requests
        // Potential vulnerabilities could exist if not properly implemented or validated.
    }
}