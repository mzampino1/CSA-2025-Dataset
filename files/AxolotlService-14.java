import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Vulnerability: Insecure Session Management
//
// The vulnerability lies in the way sessions are managed. Specifically, if an attacker can intercept a key transport message,
// they might be able to create a new session with the server without proper authentication or verification.
// This could lead to unauthorized access or man-in-the-middle attacks.

public class AxolotlService {
    private final Account account;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final Sessions sessions = new Sessions();
    private final DeviceIdManager deviceIdManager = new DeviceIdManager();

    // This is a simple in-memory store for device IDs. In a real-world application,
    // this should be more secure and persistent.
    private final Map<Jid, Set<Integer>> deviceIds = new ConcurrentHashMap<>();

    public AxolotlService(Account account) {
        this.account = account;
        fetchDeviceListIfNeeded();
    }

    private void fetchDeviceListIfNeeded() {
        // Simulate fetching the list of devices for a user
        // This is where we would normally contact the server to get the latest device IDs
        Set<Integer> ownDevices = new HashSet<>();
        ownDevices.add(getOwnDeviceId());
        deviceIds.put(account.getJid(), ownDevices);

        // For demonstration purposes, add some foreign device IDs
        Jid foreignUser = account.getJid().withLocalpart("foreign_user");
        Set<Integer> foreignDevices = new HashSet<>(Arrays.asList(1, 2));
        deviceIds.put(foreignUser, foreignDevices);
    }

    public int getOwnDeviceId() {
        // In a real-world application, this would be dynamically assigned and securely managed
        return deviceIdManager.getDeviceId(account.getJid());
    }

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<Integer> deviceIdsForContact = deviceIds.get(contact.getJid().toBareJid());
        if (deviceIdsForContact == null) {
            return Collections.emptySet();
        }
        Set<XmppAxolotlSession> sessionsForContact = new HashSet<>();
        for (Integer deviceId : deviceIdsForContact) {
            AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid(), deviceId);
            XmppAxolotlSession session = recreateUncachedSession(address);
            if (session != null) {
                sessionsForContact.add(session);
            }
        }
        return sessionsForContact;
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        Set<Integer> ownDeviceIds = deviceIds.get(account.getJid().toBareJid());
        if (ownDeviceIds == null) {
            return Collections.emptySet();
        }
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        for (Integer deviceId : ownDeviceIds) {
            AxolotlAddress address = new AxolotlAddress(account.getJid().toBareJid(), deviceId);
            XmppAxolotlSession session = recreateUncachedSession(address);
            if (session != null) {
                ownSessions.add(session);
            }
        }
        return ownSessions;
    }

    private void publishBundlesIfNeeded() {
        // This method would normally check if the key bundles are up to date and publish new ones if necessary
        // For demonstration purposes, we assume that this is handled by another part of the system
    }

    @Nullable
    private XmppAxolotlMessage buildHeader(Contact contact) {
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(
                contact.getJid().toBareJid(), getOwnDeviceId());

        Set<XmppAxolotlSession> contactSessions = findSessionsforContact(contact);
        Set<XmppAxolotlSession> ownSessions = findOwnSessions();
        if (contactSessions.isEmpty()) {
            return null;
        }
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Building axolotl foreign keyElements...");
        for (XmppAxolotlSession session : contactSessions) {
            Log.v(Config.LOGTAG, AxolotlService.getLogprefix(account) + session.getRemoteAddress().toString());
            axolotlMessage.addDevice(session);
        }
        Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Building axolotl own keyElements...");
        for (XmppAxolotlSession session : ownSessions) {
            Log.v(Config.LOGTAG, AxolotlService.getLogprefix(account) + session.getRemoteAddress().toString());
            axolotlMessage.addDevice(session);
        }

        return axolotlMessage;
    }

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        // Vulnerability: Insecure Session Management
        //
        // The vulnerability lies in the way sessions are managed. Specifically, if an attacker can intercept a key transport message,
        // they might be able to create a new session with the server without proper authentication or verification.
        // This could lead to unauthorized access or man-in-the-middle attacks.

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
                Log.w(Config.LOGTAG, getLogprefix(account) + "Failed to encrypt message: " + e.getMessage());
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
                    Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Generated message, caching: " + message.getUuid());
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
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Cache hit: " + message.getUuid());
            messageCache.remove(message.getUuid());
        } else {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Cache miss: " + message.getUuid());
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
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Account: " + account.getJid() + " No axolotl session found while parsing received message " + message);
            session = recreateUncachedSession(senderAddress);
            if (session == null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
        }
        return session;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceivingPayloadMessage(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;

        // Vulnerability: Insecure Session Management
        //
        // The vulnerability lies in the way sessions are managed. Specifically, if an attacker can intercept a key transport message,
        // they might be able to create a new session with the server without proper authentication or verification.
        // This could lead to unauthorized access or man-in-the-middle attacks.

        XmppAxolotlSession session = getReceivingSession(message);
        try {
            plaintextMessage = message.decrypt(session);
        } catch (CryptoFailedException e) {
            Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Failed to decrypt message: " + e.getMessage());
        }
        return plaintextMessage;
    }

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceivingKeyTransportMessage(XmppAxolotlMessage message) {
        // Vulnerability: Insecure Session Management
        //
        // The vulnerability lies in the way sessions are managed. Specifically, if an attacker can intercept a key transport message,
        // they might be able to create a new session with the server without proper authentication or verification.
        // This could lead to unauthorized access or man-in-the-middle attacks.

        // Normally, here we would verify the identity of the sender and other security measures
        // but for demonstration purposes, we assume that the key transport message is trusted

        XmppAxolotlSession session = getReceivingSession(message);
        try {
            session.processKeyTransportMessage(message);
        } catch (CryptoFailedException e) {
            Log.w(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Failed to process key transport message: " + e.getMessage());
        }
        return null;
    }

    private static class Sessions {
        private final Map<AxolotlAddress, XmppAxolotlSession> sessionStore = new ConcurrentHashMap<>();

        public void add(XmppAxolotlSession session) {
            sessionStore.put(session.getRemoteAddress(), session);
        }

        @Nullable
        public XmppAxolotlSession get(AxolotlAddress address) {
            return sessionStore.get(address);
        }
    }

    private static class DeviceIdManager {
        private final Map<Jid, Integer> deviceIdMap = new ConcurrentHashMap<>();

        public int getDeviceId(Jid jid) {
            return deviceIdMap.computeIfAbsent(jid, j -> {
                // Simulate device ID assignment
                Random random = new Random();
                return 100 + random.nextInt(900); // Random device IDs between 100 and 999
            });
        }
    }

    private static class AxolotlAddress {
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
            if (o == null || getClass() != o.getClass()) return false;
            AxolotlAddress that = (AxolotlAddress) o;
            return deviceId == that.deviceId && jid.equals(that.jid);
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

    private static class Jid {
        private final String localpart;
        private final String domain;

        public Jid(String localpart, String domain) {
            this.localpart = localpart;
            this.domain = domain;
        }

        public Jid withLocalpart(String localpart) {
            return new Jid(localpart, this.domain);
        }

        public Jid toBareJid() {
            return new Jid(this.localpart, this.domain);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Jid jid = (Jid) o;
            return localpart.equals(jid.localpart) && domain.equals(jid.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(localpart, domain);
        }

        @Override
        public String toString() {
            return "Jid{" +
                    "localpart='" + localpart + '\'' +
                    ", domain='" + domain + '\'' +
                    '}';
        }
    }

    private static class Account {
        private final Jid jid;

        public Account(Jid jid) {
            this.jid = jid;
        }

        public Jid getJid() {
            return jid;
        }
    }

    private static class Contact {
        private final Jid jid;

        public Contact(Jid jid) {
            this.jid = jid;
        }

        public Jid getJid() {
            return jid;
        }
    }

    private static class Message {
        private final String uuid;
        private final String body;
        private final FileParams fileParams;

        public Message(String uuid, String body, FileParams fileParams) {
            this.uuid = uuid;
            this.body = body;
            this.fileParams = fileParams;
        }

        public String getUuid() {
            return uuid;
        }

        public String getBody() {
            return body;
        }

        public FileParams getFileParams() {
            return fileParams;
        }

        public boolean hasFileOnRemoteHost() {
            return fileParams != null && fileParams.url != null;
        }
    }

    private static class FileParams {
        private final String url;

        public FileParams(String url) {
            this.url = url;
        }
    }

    private static class XmppAxolotlMessage {
        private final Jid to;
        private final int senderDeviceId;
        private Map<AxolotlAddress, byte[]> encryptedContentMap;

        public XmppAxolotlMessage(Jid to, int senderDeviceId) {
            this.to = to;
            this.senderDeviceId = senderDeviceId;
            this.encryptedContentMap = new ConcurrentHashMap<>();
        }

        public void addDevice(XmppAxolotlSession session) {
            // Add logic to create encrypted content for each device
        }

        public void encrypt(String content) throws CryptoFailedException {
            // Simulate encryption process
            if (content == null || content.isEmpty()) {
                throw new CryptoFailedException("Content cannot be empty");
            }
            // Normally, we would use a secure cryptographic library here
            for (Map.Entry<AxolotlAddress, byte[]> entry : encryptedContentMap.entrySet()) {
                AxolotlAddress address = entry.getKey();
                byte[] encryptedContent = encryptContentForDevice(content, address);
                entry.setValue(encryptedContent);
            }
        }

        private byte[] encryptContentForDevice(String content, AxolotlAddress address) throws CryptoFailedException {
            // Simulate encryption for a specific device
            if (address == null || content == null) {
                throw new CryptoFailedException("Invalid parameters");
            }
            return ("Encrypted: " + content).getBytes();
        }

        public Jid getFrom() {
            return to;
        }

        public int getSenderDeviceId() {
            return senderDeviceId;
        }

        public XmppAxolotlPlaintextMessage decrypt(XmppAxolotlSession session) throws CryptoFailedException {
            // Simulate decryption process
            byte[] encryptedContent = encryptedContentMap.get(session.getRemoteAddress());
            if (encryptedContent == null) {
                throw new CryptoFailedException("No content found for this session");
            }
            String decryptedContent = decryptContentForDevice(encryptedContent, session);
            return new XmppAxolotlPlaintextMessage(decryptedContent);
        }

        private String decryptContentForDevice(byte[] encryptedContent, XmppAxolotlSession session) throws CryptoFailedException {
            // Simulate decryption for a specific device
            if (session == null || encryptedContent == null) {
                throw new CryptoFailedException("Invalid parameters");
            }
            return new String(encryptedContent).replaceFirst("Encrypted: ", "");
        }

        public void processKeyTransportMessage(XmppAxolotlSession session) throws CryptoFailedException {
            // Simulate processing of key transport message
            if (session == null) {
                throw new CryptoFailedException("Invalid session");
            }
            // Normally, we would verify the identity and other security measures here
        }
    }

    private static class XmppAxolotlPlaintextMessage {
        private final String content;

        public XmppAxolotlPlaintextMessage(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    private static class XmppAxolotlSession {
        private final Account account;
        private final AxolotlStore axolotlStore;
        private final AxolotlAddress remoteAddress;
        private final String fingerprint;

        public XmppAxolotlSession(Account account, AxolotlStore axolotlStore, AxolotlAddress remoteAddress, String fingerprint) {
            this.account = account;
            this.axolotlStore = axolotlStore;
            this.remoteAddress = remoteAddress;
            this.fingerprint = fingerprint;
        }

        public void processKeyTransportMessage(XmppAxolotlMessage message) throws CryptoFailedException {
            // Simulate processing of key transport message
            if (message == null) {
                throw new CryptoFailedException("Invalid message");
            }
            // Normally, we would verify the identity and other security measures here
        }

        public boolean isTrusted() {
            // Check if the session is trusted
            return true;
        }

        public AxolotlAddress getRemoteAddress() {
            return remoteAddress;
        }
    }

    private static class AxolotlStore {
        // Store cryptographic keys and other session data
    }

    private static class CryptoFailedException extends Exception {
        public CryptoFailedException(String message) {
            super(message);
        }
    }

    private static class Config {
        private static final String LOG_TAG = "AxolotlService";

        private Config() {
            // Private constructor to prevent instantiation
        }
    }

    private static class Log {
        public static void w(String tag, String msg) {
            System.out.println("WARN: " + tag + ": " + msg);
        }
    }
}