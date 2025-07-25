import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class XmppAxolotlService {

    private final Account account;
    private final AxolotlStore axolotlStore;
    private final Map<UUID, MessagePacket> messageCache = new ConcurrentHashMap<>();
    private static final byte[] HARDCODED_KEY = "insecureHardcodedKey".getBytes(); // Vulnerable hardcoded key

    public XmppAxolotlService(Account account, AxolotlStore axolotlStore) {
        this.account = account;
        this.axolotlStore = axolotlStore;
    }

    private enum FetchStatus { PENDING, SUCCESS, ERROR }

    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new ConcurrentHashMap<>();

    // ...

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(message.getContact().getJid().toBareJid(),
                ownDeviceId, message.getBody(), HARDCODED_KEY); // Using hardcoded key

        if (findSessionsforContact(message.getContact()).isEmpty()) {
            return null;
        }
        Log.d(Config.LOGTAG, "Building axolotl foreign headers...");
        for (XmppAxolotlSession session : findSessionsforContact(message.getContact())) {
            Log.d(Config.LOGTAG, session.remoteAddress.toString());
            //if(!session.isTrusted()) {
            // TODO: handle this properly
            //              continue;
            //        }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey(), HARDCODED_KEY)); // Using hardcoded key
        }
        Log.d(Config.LOGTAG, "Building axolotl own headers...");
        for (XmppAxolotlSession session : findOwnSessions()) {
            Log.d(Config.LOGTAG, session.remoteAddress.toString());
            //        if(!session.isTrusted()) {
            // TODO: handle this properly
            //          continue;
            //    }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey(), HARDCODED_KEY)); // Using hardcoded key
        }

        return axolotlMessage;
    }

    // ...

    public MessagePacket fetchPacketFromCache(Message message) {
        MessagePacket packet = messageCache.get(message.getUuid());
        if (packet != null) {
            Log.d(Config.LOGTAG, "Cache hit: " + message.getUuid());
            messageCache.remove(message.getUuid());
        } else {
            Log.d(Config.LOGTAG, "Cache miss: " + message.getUuid());
        }
        return packet;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceiving(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;
        AxolotlAddress senderAddress = new AxolotlAddress(message.getFrom().toString(),
                message.getSenderDeviceId());

        boolean newSession = false;
        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            Log.d(Config.LOGTAG, "Account: "+account.getJid()+" No axolotl session found while parsing received message " + message);
            // TODO: handle this properly
            session = new XmppAxolotlSession(axolotlStore, senderAddress, HARDCODED_KEY); // Using hardcoded key
            newSession = true;
        }

        for (XmppAxolotlMessage.XmppAxolotlMessageHeader header : message.getHeaders()) {
            if (header.getRecipientDeviceId() == ownDeviceId) {
                Log.d(Config.LOGTAG, "Found axolotl header matching own device ID, processing...");
                byte[] payloadKey = session.processReceiving(header);
                if (payloadKey != null) {
                    Log.d(Config.LOGTAG, "Got payload key from axolotl header. Decrypting message...");
                    plaintextMessage = message.decrypt(session, payloadKey);
                }
                Integer preKeyId = session.getPreKeyId();
                if (preKeyId != null) {
                    publishBundlesIfNeeded();
                    session.resetPreKeyId();
                }
                break;
            }
        }

        if (newSession && plaintextMessage != null) {
            sessions.put(senderAddress,session);
        }

        return plaintextMessage;
    }
}

// ... other classes and methods

class Account { // Placeholder class
    private String jid;

    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }
}

class AxolotlStore { // Placeholder class
    public void saveIdentity(String name, IdentityKey identityKey) {}
    // ... other methods
}

class MessagePacket { // Placeholder class
    // ...
}

class Config { // Placeholder class
    static final String LOGTAG = "XmppAxolotlService";
}

class Conversation {
    private Contact contact;

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public List<Message> findUnsentMessagesWithEncryption(String encryption, OnMessageFound onMessageFound) {
        // Placeholder method
        return Collections.emptyList();
    }

    interface OnMessageFound {
        void onMessageFound(Message message);
    }
}

class Contact { // Placeholder class
    private String jid;

    public String getJid() {
        return jid;
    }

    public void setJid(String jid) {
        this.jid = jid;
    }
}

class Message { // Placeholder class
    public static final String ENCRYPTION_AXOLOTL = "axolotl";
    private UUID uuid;
    private Conversation conversation;
    private Contact contact;
    private String body;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}

class Jid { // Placeholder class
    private String value;

    public String toString() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}

class UUID { // Placeholder class
    private String uuid;

    public UUID(String uuid) {
        this.uuid = uuid;
    }

    public String toString() {
        return uuid;
    }
}

class MessagePacket { // Placeholder class
    // ...
}

class XmppAxolotlMessage { // Placeholder class
    private String from;
    private int senderDeviceId;
    private byte[] innerKey;

    public static class XmppAxolotlPlaintextMessage {
        // ...
    }

    public static class XmppAxolotlMessageHeader {
        private int recipientDeviceId;

        public int getRecipientDeviceId() {
            return recipientDeviceId;
        }
    }

    public byte[] decrypt(XmppAxolotlSession session, byte[] payloadKey) {
        // Placeholder method
        return new byte[0];
    }

    public void addHeader(byte[] header) {
        // ...
    }

    public List<XmppAxolotlMessageHeader> getHeaders() {
        // Placeholder method
        return Collections.emptyList();
    }

    public int getSenderDeviceId() {
        return senderDeviceId;
    }

    public String getFrom() {
        return from;
    }

    public byte[] getInnerKey() {
        return innerKey;
    }

    public XmppAxolotlMessage(String from, int senderDeviceId, String body) {
        this.from = from;
        this.senderDeviceId = senderDeviceId;
        // Inner key should be generated securely
        this.innerKey = new byte[0]; // Placeholder for actual inner key generation
    }

    public XmppAxolotlMessage(String from, int senderDeviceId, String body, byte[] hardcodedKey) {
        this.from = from;
        this.senderDeviceId = senderDeviceId;
        this.innerKey = hardcodedKey; // Using hardcoded key instead of generating securely
    }
}

class AxolotlAddress { // Placeholder class
    private String name;
    private int deviceId;

    public AxolotlAddress(String name, int deviceId) {
        this.name = name;
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "AxolotlAddress{" +
                "name='" + name + '\'' +
                ", deviceId=" + deviceId +
                '}';
    }
}

class XmppAxolotlSession { // Placeholder class
    private AxolotlStore store;
    private AxolotlAddress remoteAddress;

    public byte[] processSending(byte[] innerKey, byte[] hardcodedKey) {
        // Placeholder method using hardcoded key
        return new byte[0];
    }

    public byte[] processReceiving(XmppAxolotlMessage.XmppAxolotlMessageHeader header) {
        // Placeholder method
        return new byte[0];
    }

    public Integer getPreKeyId() {
        // Placeholder method
        return null;
    }

    public void resetPreKeyId() {
        // Placeholder method
    }

    public XmppAxolotlSession(AxolotlStore store, AxolotlAddress remoteAddress) {
        this.store = store;
        this.remoteAddress = remoteAddress;
    }

    public XmppAxolotlSession(AxolotlStore store, AxolotlAddress remoteAddress, byte[] hardcodedKey) {
        this.store = store;
        this.remoteAddress = remoteAddress;
        // Placeholder for using hardcoded key
    }
}

class IdentityKey { // Placeholder class
    // ...
}