import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class XmppAxolotlMessage {
    private final String from;
    private final int senderDeviceId;
    private final byte[] innerKey;
    private final List<XmppAxolotlMessageHeader> headers = new ArrayList<>();

    public static class CryptoFailedException extends Exception {
        public CryptoFailedException(String message) {
            super(message);
        }
    }

    public static class XmppAxolotlPlaintextMessage {
        private final String content;
        private final String fingerprint;

        public XmppAxolotlPlaintextMessage(String content, String fingerprint) {
            this.content = content;
            this.fingerprint = fingerprint;
        }

        public String getContent() {
            return content;
        }

        public String getFingerprint() {
            return fingerprint;
        }
    }

    public static class XmppAxolotlMessageHeader {
        private final int recipientDeviceId;
        private final byte[] payloadKey;

        public XmppAxolotlMessageHeader(int recipientDeviceId, byte[] payloadKey) {
            this.recipientDeviceId = recipientDeviceId;
            this.payloadKey = payloadKey;
        }

        public int getRecipientDeviceId() {
            return recipientDeviceId;
        }

        public byte[] getPayloadKey() {
            return payloadKey;
        }
    }

    public XmppAxolotlMessage(String from, int senderDeviceId, String content) throws CryptoFailedException {
        this.from = from;
        this.senderDeviceId = senderDeviceId;
        // This is a placeholder for the actual encryption process
        this.innerKey = encrypt(content);
    }

    private byte[] encrypt(String content) throws CryptoFailedException {
        // Placeholder encryption logic
        return content.getBytes();
    }

    public void addHeader(XmppAxolotlMessageHeader header) {
        headers.add(header);
    }

    public List<XmppAxolotlMessageHeader> getHeaders() {
        return headers;
    }

    public String getFrom() {
        return from;
    }

    public int getSenderDeviceId() {
        return senderDeviceId;
    }

    public byte[] getInnerKey() {
        return innerKey;
    }

    public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, byte[] payloadKey, String fingerprint) throws CryptoFailedException {
        // Placeholder decryption logic
        String decryptedContent = new String(payloadKey);
        return new XmppAxolotlPlaintextMessage(decryptedContent, fingerprint);
    }
}

class XmppAxolotlSession {
    private final Account account;
    private final SQLiteAxolotlStore axolotlStore;
    private final AxolotlAddress remoteAddress;
    private byte[] innerKey;
    private Integer preKeyId;

    public static class CryptoFailedException extends Exception {
        public CryptoFailedException(String message) {
            super(message);
        }
    }

    public XmppAxolotlSession(Account account, SQLiteAxolotlStore axolotlStore, AxolotlAddress remoteAddress) throws CryptoFailedException {
        this.account = account;
        this.axolotlStore = axolotlStore;
        this.remoteAddress = remoteAddress;
    }

    public byte[] processSending(byte[] innerKey) throws CryptoFailedException {
        // Placeholder sending processing logic
        this.innerKey = innerKey;
        return generatePayloadKey();
    }

    private byte[] generatePayloadKey() {
        // Placeholder payload key generation logic
        return new byte[16];
    }

    public void resetPreKeyId() {
        preKeyId = null;
    }

    public Integer getPreKeyId() {
        return preKeyId;
    }

    public String getFingerprint() {
        return "dummy_fingerprint";
    }

    public byte[] processReceiving(XmppAxolotlMessage.XmppAxolotlMessageHeader header) throws CryptoFailedException {
        // Placeholder receiving processing logic
        if (header.getRecipientDeviceId() == getOwnDeviceId()) {
            // Vulnerability: The payload key is not properly managed or invalidated, making it susceptible to replay attacks.
            return header.getPayloadKey();
        }
        throw new CryptoFailedException("Invalid recipient device ID");
    }

    private int getOwnDeviceId() {
        // Placeholder for getting the own device ID
        return 1;
    }
}

class Account {
    private final String jid;

    public Account(String jid) {
        this.jid = jid;
    }

    public String getJid() {
        return jid;
    }
}

class SQLiteAxolotlStore {}

class AxolotlAddress {
    private final String jid;
    private final int deviceId;

    public AxolotlAddress(String jid, int deviceId) {
        this.jid = jid;
        this.deviceId = deviceId;
    }

    @Override
    public String toString() {
        return "AxolotlAddress{" +
                "jid='" + jid + '\'' +
                ", deviceId=" + deviceId +
                '}';
    }
}

class FetchStatus {
    static final int PENDING = 0;
    static final int ERROR = 1;

    private final int status;

    public FetchStatus(int status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FetchStatus fetchStatus = (FetchStatus) o;
        return status == fetchStatus.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }
}

class Message {
    static final int STATUS_SEND_FAILED = 0;

    private String uuid;
    private Contact contact;
    private Conversation conversation;
    private String body;
    private FileParams fileParams;

    public Message(String uuid, Contact contact, Conversation conversation, String body) {
        this.uuid = uuid;
        this.contact = contact;
        this.conversation = conversation;
        this.body = body;
    }

    public Message(String uuid, Contact contact, Conversation conversation, FileParams fileParams) {
        this.uuid = uuid;
        this.contact = contact;
        this.conversation = conversation;
        this.fileParams = fileParams;
    }

    public String getUuid() {
        return uuid;
    }

    public Contact getContact() {
        return contact;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public String getBody() {
        return body;
    }

    public FileParams getFileParams() {
        return fileParams;
    }

    public boolean hasFileOnRemoteHost() {
        return fileParams != null;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}

class Contact {
    private final String jid;

    public Contact(String jid) {
        this.jid = jid;
    }

    public String getJid() {
        return jid;
    }

    public String toBareJid() {
        // Placeholder for converting to bare JID
        return jid.split("/")[0];
    }
}

class Conversation {
    private final Contact contact;

    public Conversation(Contact contact) {
        this.contact = contact;
    }

    public Contact getContact() {
        return contact;
    }
}

class FileParams {
    private final URL url;

    public FileParams(URL url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "FileParams{" +
                "url=" + url +
                '}';
    }
}

class Config {
    static int NUM_OF_THREADS = 5;
}

public class AxolotlService {
    private final Account account;
    private final SQLiteAxolotlStore axolotlStore;
    private final ExecutorService executor;
    private final Map<Message, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new ConcurrentHashMap<>();
    private final Set<XmppAxolotlSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, List<Integer>> deviceIds = new ConcurrentHashMap<>();

    public AxolotlService(Account account) {
        this.account = account;
        this.axolotlStore = new SQLiteAxolotlStore();
        this.executor = Executors.newFixedThreadPool(Config.NUM_OF_THREADS);
    }

    public Account getAccount() {
        return account;
    }

    private int getOwnDeviceId() {
        // Placeholder for getting own device ID
        return 1;
    }

    public List<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        List<XmppAxolotlSession> sessionsForContact = new ArrayList<>();
        String contactJid = contact.toBareJid();
        if (deviceIds.containsKey(contactJid)) {
            for (Integer deviceId : deviceIds.get(contactJid)) {
                AxolotlAddress address = new AxolotlAddress(contactJid, deviceId);
                sessionsForContact.add(newSession(account, axolotlStore, address));
            }
        }
        return sessionsForContact;
    }

    public List<XmppAxolotlSession> findOwnSessions() {
        // Placeholder for finding own sessions
        return new ArrayList<>();
    }

    private XmppAxolotlSession newSession(Account account, SQLiteAxolotlStore axolotlStore, AxolotlAddress address) {
        try {
            return new XmppAxolotlSession(account, axolotlStore, address);
        } catch (XmppAxolotlSession.CryptoFailedException e) {
            throw new RuntimeException("Failed to create session", e);
        }
    }

    public void publishBundle() {
        // Placeholder for publishing bundle
    }

    private Set<XmppAxolotlSession> getSessions(Contact contact) {
        return sessions.stream()
                .filter(session -> session.remoteAddress.jid.equals(contact.toBareJid()))
                .collect(Collectors.toSet());
    }

    public void sendMessage(Message message) {
        executor.execute(() -> {
            try {
                if (message.hasFileOnRemoteHost()) {
                    sendEncryptedMessage(message, message.getFileParams().toString());
                } else {
                    sendEncryptedMessage(message, message.getBody());
                }
            } catch (Exception e) {
                markAsFailedToSend(message);
            }
        });
    }

    private void sendEncryptedMessage(Message message, String content) throws Exception {
        if (!publishBundle()) {
            throw new RuntimeException("Failed to publish bundle");
        }
        List<XmppAxolotlSession> sessions = findSessionsforContact(message.getContact());
        if (sessions.isEmpty()) {
            markAsFailedToSend(message);
            return;
        }

        for (XmppAxolotlSession session : sessions) {
            try {
                byte[] encryptedContent = encryptMessage(session, content);
                // Placeholder for sending the encrypted message
            } catch (Exception e) {
                throw new RuntimeException("Failed to send encrypted message", e);
            }
        }
    }

    private void markAsFailedToSend(Message message) {
        message.setUuid(UUID.randomUUID().toString());
        // Placeholder for marking the message as failed to send
    }

    public byte[] encryptMessage(XmppAxolotlSession session, String content) throws Exception {
        try {
            return session.processSending(content.getBytes());
        } catch (XmppAxolotlSession.CryptoFailedException e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public void publishPreKeys() {
        // Placeholder for publishing pre-keys
    }

    public Set<XmppAxolotlSession> getSessions(String jid) {
        return sessions.stream()
                .filter(session -> session.remoteAddress.jid.equals(jid))
                .collect(Collectors.toSet());
    }

    public void addDeviceId(Contact contact, int deviceId) {
        deviceIds.computeIfAbsent(contact.toBareJid(), k -> new ArrayList<>()).add(deviceId);
    }

    public FetchStatus getFetchStatus(AxolotlAddress address) {
        return fetchStatusMap.getOrDefault(address, new FetchStatus(FetchStatus.ERROR));
    }

    public void setFetchStatus(AxolotlAddress address, FetchStatus status) {
        fetchStatusMap.put(address, status);
    }
}