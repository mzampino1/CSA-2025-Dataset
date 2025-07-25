import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {
    private final Account account;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Map<String, List<Integer>> deviceIds = new ConcurrentHashMap<>();
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new ConcurrentHashMap<>();
    private final Map<AxolotlAddress, XmppAxolotlSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final AxolotlStore axolotlStore;

    public enum FetchStatus {
        PENDING,
        ERROR
    }

    public AxolotlService(Account account) {
        this.account = account;
        this.axolotlStore = new AxolotlStore(account.getJid().toBareJid());
    }

    private int getOwnDeviceId() {
        return 1; // Assuming device ID is always 1 for simplicity
    }

    public void updateDeviceIds(Map<String, List<Integer>> newDeviceIds) {
        this.deviceIds.clear();
        this.deviceIds.putAll(newDeviceIds);
        publishBundlesIfNeeded(true); // Potential vulnerability: Bundles are published every time device IDs are updated
    }

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<XmppAxolotlSession> sessionsForContact = new HashSet<>();
        if (deviceIds.containsKey(contact.getJid().toBareJid())) {
            for (Integer deviceId : deviceIds.get(contact.getJid().toBareJid())) {
                AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid(), deviceId);
                sessionsForContact.add(sessions.get(address));
            }
        }

        return sessionsForContact;
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        if (deviceIds.containsKey(account.getJid().toBareJid())) {
            for (Integer deviceId : deviceIds.get(account.getJid().toBareJid())) {
                AxolotlAddress address = new AxolotlAddress(account.getJid().toBareJid(), deviceId);
                ownSessions.add(sessions.get(address));
            }
        }

        return ownSessions;
    }

    public void publishBundlesIfNeeded(boolean force) {
        // Potential vulnerability: This method is called frequently which can lead to unnecessary bundle publication
        if (force || needsBundlePublication()) {
            Log.i(Config.TAG, getLogprefix(account) + "Publishing bundles...");
            publishBundles();
        }
    }

    private boolean needsBundlePublication() {
        // Simplified condition for demonstration purposes
        return true;
    }

    private void publishBundles() {
        // Implementation of bundle publication
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    AxolotlMessage header = buildHeader(account.getJid().toBareJid());
                    if (header != null) {
                        Log.i(Config.TAG, getLogprefix(account) + "Generated message");
                        // Send the bundle publication message
                    }
                } catch (Exception e) {
                    Log.e(Config.TAG, getLogprefix(account) + "Failed to publish bundles: " + e.getMessage());
                }
            }
        });
    }

    @Nullable
    private AxolotlMessage buildHeader(Jid jid) {
        final AxolotlMessage axolotlMessage = new AxolotlMessage(jid, getOwnDeviceId());

        Set<XmppAxolotlSession> contactSessions = findSessionsforContact(new Contact(jid));
        Set<XmppAxolotlSession> ownSessions = findOwnSessions();
        if (contactSessions.isEmpty()) {
            return null;
        }
        Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Building axolotl foreign keyElements...");
        for (XmppAxolotlSession session : contactSessions) {
            Log.v(Config.TAG, AxolotlService.getLogprefix(account) + session.getRemoteAddress().toString());
            axolotlMessage.addDevice(session);
        }
        Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Building axolotl own keyElements...");
        for (XmppAxolotlSession session : ownSessions) {
            Log.v(Config.TAG, AxolotlService.getLogprefix(account) + session.getRemoteAddress().toString());
            axolotlMessage.addDevice(session);
        }

        return axolotlMessage;
    }

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        XmppAxolotlMessage axolotlMessage = buildHeader(message.getContact());

        if (axolotlMessage != null) {
            final String content;
            if (message.hasFileOnRemoteHost()) {
                content = message.getFileParams().url.toString();
            } else {
                content = message.getBody();
            }
            try {
                axolotlMessage.encrypt(content);
            } catch (CryptoFailedException e) {
                Log.w(Config.TAG, getLogprefix(account) + "Failed to encrypt message: " + e.getMessage());
                return null;
            }
        }

        return axolotlMessage;
    }

    public void preparePayloadMessage(final Message message, final boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                XmppAxolotlMessage axolotlMessage = encrypt(message);
                if (axolotlMessage == null) {
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                    //mXmppConnectionService.updateConversationUi();
                } else {
                    Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                    messageCache.put(message.getUuid(), axolotlMessage);
                    mXmppConnectionService.resendMessage(message, delay);
                }
            }
        });
    }

    public void prepareKeyTransportMessage(final Contact contact, final OnMessageCreatedCallback onMessageCreatedCallback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                XmppAxolotlMessage axolotlMessage = buildHeader(contact);
                onMessageCreatedCallback.run(axolotlMessage);
            }
        });
    }

    public XmppAxolotlMessage fetchAxolotlMessageFromCache(Message message) {
        XmppAxolotlMessage axolotlMessage = messageCache.get(message.getUuid());
        if (axolotlMessage != null) {
            Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Cache hit: " + message.getUuid());
            messageCache.remove(message.getUuid());
        } else {
            Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Cache miss: " + message.getUuid());
        }
        return axolotlMessage;
    }

    private XmppAxolotlSession recreateUncachedSession(AxolotlAddress address) {
        IdentityKey identityKey = axolotlStore.loadSession(address).getSessionState().getRemoteIdentityKey();
        return (identityKey != null)
                ? new XmppAxolotlSession(account, axolotlStore, address,
                        identityKey.getFingerprint().replaceAll("\\s", ""))
                : null;
    }

    private XmppAxolotlSession getReceivingSession(XmppAxolotlMessage message) {
        AxolotlAddress senderAddress = new AxolotlAddress(message.getFrom().toString(),
                message.getSenderDeviceId());
        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            Log.d(Config.TAG, AxolotlService.getLogprefix(account) + "Account: " + account.getJid() + " No axolotl session found while parsing received message " + message);
            session = recreateUncachedSession(senderAddress);
            if (session == null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
        }
        return session;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceivingPayloadMessage(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;

        XmppAxolotlSession session = getReceivingSession(message);
        try {
            plaintextMessage = message.decrypt(session, getOwnDeviceId());
            Integer preKeyId = session.getPreKeyId();
            if (preKeyId != null) {
                publishBundlesIfNeeded(false);
                session.resetPreKeyId();
            }
        } catch (CryptoFailedException e) {
            Log.w(Config.TAG, getLogprefix(account) + "Failed to decrypt message: " + e.getMessage());
        }

        if (session.isFresh() && plaintextMessage != null) {
            sessions.put(session);
        }

        return plaintextMessage;
    }

    public XmppAxolotlMessage.XmppAxolotlKeyTransportMessage processReceivingKeyTransportMessage(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlKeyTransportMessage keyTransportMessage = null;

        XmppAxolotlSession session = getReceivingSession(message);
        try {
            keyTransportMessage = message.processKeyTransport(session, getOwnDeviceId());
            Integer preKeyId = session.getPreKeyId();
            if (preKeyId != null) {
                publishBundlesIfNeeded(false);
                session.resetPreKeyId();
            }
        } catch (CryptoFailedException e) {
            Log.w(Config.TAG, getLogprefix(account) + "Failed to process key transport message: " + e.getMessage());
        }

        if (session.isFresh() && keyTransportMessage != null) {
            sessions.put(session);
        }

        return keyTransportMessage;
    }

    private String getLogprefix(Account account) {
        return "[Account: " + account.getJid().toBareJid() + "] ";
    }
}

class Account {
    private final Jid jid;

    public Account(Jid jid) {
        this.jid = jid;
    }

    public Jid getJid() {
        return jid;
    }
}

class Contact {
    private final Jid jid;

    public Contact(Jid jid) {
        this.jid = jid;
    }

    public Jid getJid() {
        return jid;
    }
}

class Message {
    public static final int STATUS_SEND_FAILED = 0;
    private String uuid;
    private Contact contact;
    private FileParams fileParams;

    public Message(String uuid, Contact contact) {
        this.uuid = uuid;
        this.contact = contact;
    }

    public String getUuid() {
        return uuid;
    }

    public Contact getContact() {
        return contact;
    }

    public boolean hasFileOnRemoteHost() {
        // Simplified condition for demonstration purposes
        return fileParams != null;
    }

    public FileParams getFileParams() {
        return fileParams;
    }

    public String getBody() {
        // Return a dummy body for demonstration purposes
        return "Dummy Message Body";
    }
}

class FileParams {
    private URL url;

    public FileParams(URL url) {
        this.url = url;
    }

    public URL getUrl() {
        return url;
    }
}

class Jid {
    private final String bareJid;

    public Jid(String bareJid) {
        this.bareJid = bareJid;
    }

    public String toBareJid() {
        return bareJid;
    }
}

class Config {
    public static final String TAG = "AxolotlService";
}

class Log {
    public static void i(String tag, String message) {
        System.out.println("INFO: [" + tag + "] " + message);
    }

    public static void w(String tag, String message) {
        System.out.println("WARN: [" + tag + "] " + message);
    }

    public static void e(String tag, String message) {
        System.out.println("ERROR: [" + tag + "] " + message);
    }
}

class CryptoFailedException extends Exception {
    public CryptoFailedException(String message) {
        super(message);
    }
}

class AxolotlStore {
    private final Jid accountJid;

    public AxolotlStore(Jid accountJid) {
        this.accountJid = accountJid;
    }

    public Session loadSession(AxolotlAddress address) {
        // Dummy implementation
        return new Session(address);
    }
}

class Session {
    private final AxolotlAddress address;

    public Session(AxolotlAddress address) {
        this.address = address;
    }

    public SessionState getSessionState() {
        return new SessionState();
    }

    public void addDevice(XmppAxolotlSession session) {
        // Dummy implementation
    }
}

class SessionState {
    public IdentityKey getRemoteIdentityKey() {
        // Dummy implementation
        return new IdentityKey("dummyFingerprint");
    }
}

class IdentityKey {
    private final String fingerprint;

    public IdentityKey(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public String getFingerprint() {
        return fingerprint;
    }
}

interface OnMessageCreatedCallback {
    void run(XmppAxolotlMessage axolotlMessage);
}

class AxolotlMessage {
    private Jid from;
    private int senderDeviceId;

    public AxolotlMessage(Jid jid, int deviceId) {
        this.from = jid;
        this.senderDeviceId = deviceId;
    }

    public void addDevice(XmppAxolotlSession session) {
        // Dummy implementation
    }

    public Jid getFrom() {
        return from;
    }

    public int getSenderDeviceId() {
        return senderDeviceId;
    }
}

class XmppAxolotlMessage extends AxolotlMessage {
    private String content;

    public XmppAxolotlMessage(Jid jid, int deviceId) {
        super(jid, deviceId);
    }

    public void encrypt(String content) throws CryptoFailedException {
        // Dummy encryption implementation
        this.content = "encrypted:" + content;
    }

    public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session, int ownDeviceId) throws CryptoFailedException {
        if (content == null || content.isEmpty()) {
            throw new CryptoFailedException("Content is empty");
        }
        // Dummy decryption implementation
        String decryptedContent = content.replace("encrypted:", "");
        return new XmppAxolotlPlaintextMessage(decryptedContent);
    }

    public XmppAxolotlKeyTransportMessage processKeyTransport(XmppAxolotlSession session, int ownDeviceId) throws CryptoFailedException {
        // Dummy key transport processing implementation
        return new XmppAxolotlKeyTransportMessage("dummyKey");
    }
}

class XmppAxolotlPlaintextMessage {
    private String content;

    public XmppAxolotlPlaintextMessage(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }
}

class XmppAxolotlKeyTransportMessage {
    private String key;

    public XmppAxolotlKeyTransportMessage(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}

class AxolotlAddress {
    private final Jid jid;
    private final int deviceId;

    public AxolotlAddress(Jid jid, int deviceId) {
        this.jid = jid;
        this.deviceId = deviceId;
    }

    public Jid getJid() {
        return jid;
    }

    public int getDeviceId() {
        return deviceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AxolotlAddress)) return false;
        AxolotlAddress that = (AxolotlAddress) o;
        return deviceId == that.deviceId && Objects.equals(jid, that.jid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jid, deviceId);
    }

    @Override
    public String toString() {
        return "AxolotlAddress{" +
                "jid=" + jid +
                ", deviceId=" + deviceId +
                '}';
    }
}

class XmppAxolotlSession {
    private final Account account;
    private final AxolotlStore axolotlStore;
    private final AxolotlAddress address;
    private String fingerprint;
    private Integer preKeyId;

    public XmppAxolotlSession(Account account, AxolotlStore axolotlStore, AxolotlAddress address) {
        this.account = account;
        this.axolotlStore = axolotlStore;
        this.address = address;
    }

    public void addDevice(XmppAxolotlSession session) {
        // Dummy implementation
    }

    public AxolotlAddress getRemoteAddress() {
        return address;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public boolean isFresh() {
        // Dummy condition for demonstration purposes
        return true;
    }

    public void resetPreKeyId() {
        preKeyId = null;
    }

    public Integer getPreKeyId() {
        return preKeyId;
    }
}

class MxppConnectionService {
    public void markMessage(Message message, int status) {
        // Dummy implementation
    }

    public void resendMessage(Message message, boolean delay) {
        // Dummy implementation
    }

    public void updateConversationUi() {
        // Dummy implementation
    }
}