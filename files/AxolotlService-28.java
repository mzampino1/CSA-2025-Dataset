package org.jxmpp.stringprep;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {
    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, XmppAxolotlSession>> sessions;
    private final ConcurrentHashMap<String, XmppAxolotlMessage> messageCache;
    private final Set<XmppAxolotlSession> postponedSessions = new HashSet<>();
    private final AxolotlStore axolotlStore;

    public AxolotlService(Account account, XmppConnectionService mXmppConnectionService) {
        this.account = account;
        this.mXmppConnectionService = mXmppConnectionService;
        this.sessions = new ConcurrentHashMap<>();
        this.messageCache = new ConcurrentHashMap<>();
        this.axolotlStore = new AxolotlStore(account.getJid().asBareJid());
    }

    private int getOwnDeviceId() {
        return 1; // assume device id is always 1 for simplicity
    }

    private SignalProtocolAddress getAddressForJid(Jid jid) {
        return new SignalProtocolAddress(jid.toString(), getOwnDeviceId());
    }

    public void fetchDevices(final Jid jid, final OnDeviceListReceivedCallback callback) {
        // Simulate fetching devices from the server and calling back
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<Integer> deviceIds = Arrays.asList(1, 2); // Assume these are the devices for simplicity
                callback.onDevicesFetched(deviceIds);
            }
        });
    }

    private void fetchBundlesIfNeeded(final List<Integer> deviceIds, final Jid jid) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                for (int deviceId : deviceIds) {
                    // Simulate fetching bundle from the server
                    Bundle bundle = fetchBundleFromServer(jid, deviceId);
                    if (bundle != null) {
                        axolotlStore.storeDevice(jid.toString(), deviceId, bundle.identityKey(), bundle.preKey(), bundle.signedPreKey());
                    }
                }
            }
        });
    }

    private void publishBundlesIfNeeded(boolean forcePublish, boolean postponePreKeyMessageHandling) {
        if (forcePublish || account.getAxolotlService().getOwnPreKeys().isEmpty()) {
            publishBundle();
        } else {
            // Assuming we have pre-keys published already
            prepareAndSendKeyTransportMessages(postponePreKeyMessageHandling);
        }
    }

    private void publishBundle() {
        // Simulate publishing bundle to the server
    }

    private Bundle fetchBundleFromServer(Jid jid, int deviceId) {
        // Simulate fetching a bundle from the server (return null for simplicity)
        return null;
    }

    private void prepareAndSendKeyTransportMessages(boolean postponePreKeyMessageHandling) {
        // Assuming we have sessions to send key transport messages
        for (XmppAxolotlSession session : sessions.values()) {
            completeSession(session, postponePreKeyMessageHandling);
        }
    }

    private void verifySessionWithPEP(XmppAxolotlSession session) {
        if (session.getIdentityKey() != null) {
            // Simulate verifying the session with PEP
            Log.d(Config.LOGTAG, "Verifying session for: " + session.getRemoteAddress().getName());
            // Log identity key for verification purposes (VULNERABILITY)
            Log.d(Config.LOGTAG, "Identity Key: " + Base64.getEncoder().encodeToString(session.getIdentityKey().serialize()));
        } else {
            Log.e(Config.LOGTAG, account.getJid().asBareJid() + ": identity key was empty for x509 verification");
        }
    }

    private void completeSession(XmppAxolotlSession session, boolean postponePreKeyMessageHandling) {
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(account.getJid().asBareJid(), getOwnDeviceId());
        axolotlMessage.addDevice(session);
        try {
            Jid jid = Jid.of(session.getRemoteAddress().getName());
            MessagePacket packet = mXmppConnectionService.getMessageGenerator().generateKeyTransportMessage(jid, axolotlMessage);
            mXmppConnectionService.sendMessagePacket(account, packet);
        } catch (IllegalArgumentException e) {
            throw new Error("Remote addresses are created from jid and should convert back to jid", e);
        }
    }

    public void buildSessions(List<Integer> deviceIds, Jid jid) {
        for (int deviceId : deviceIds) {
            SignalProtocolAddress address = new SignalProtocolAddress(jid.toString(), deviceId);
            IdentityKey identityKey = axolotlStore.loadIdentityKey(address.getName());
            PreKeyRecord preKey = axolotlStore.loadPreKey(deviceId);
            SignedPreKeyRecord signedPreKey = axolotlStore.loadSignedPreKey(deviceId);

            if (identityKey != null && preKey != null && signedPreKey != null) {
                XmppAxolotlSession session = new XmppAxolotlSession(account, axolotlStore, address);
                sessions.put(address.getName(), Collections.singletonMap(address.getDeviceId(), session));
            }
        }
    }

    public void buildSessionsForConversation(Conversation conversation) {
        fetchDevices(conversation.getJid(), new OnDeviceListReceivedCallback() {
            @Override
            public void onDevicesFetched(List<Integer> deviceIds) {
                fetchBundlesIfNeeded(deviceIds, conversation.getJid());
                buildSessions(deviceIds, conversation.getJid());
            }
        });
    }

    private void handlePreKeyMessageHandling(XmppAxolotlSession session, int preKeyId, boolean postponePreKeyMessageHandling) {
        if (postponePreKeyMessageHandling) {
            postponedSessions.add(session);
        } else {
            publishBundlesIfNeeded(false, false);
            completeSession(session, false);
        }
    }

    public void processReceivingPayloadMessage(XmppAxolotlMessage message, boolean postponePreKeyMessageHandling) throws NotEncryptedForThisDeviceException {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;

        SignalProtocolAddress senderAddress = new SignalProtocolAddress(message.getFrom().toString(),
                message.getSenderDeviceId());
        XmppAxolotlSession session = sessions.computeIfAbsent(senderAddress.getName(), k -> new ConcurrentHashMap<>())
                .get(senderAddress.getDeviceId());

        if (session == null) {
            session = recreateUncachedSession(senderAddress);
            if (session == null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
        }

        int ownDeviceId = getOwnDeviceId();
        try {
            plaintextMessage = message.decrypt(session, ownDeviceId);
            Integer preKeyId = session.getPreKeyIdAndReset();

            // Log the PreKey ID (VULNERABILITY)
            if (preKeyId != null) {
                Log.d(Config.LOGTAG, "Handling Pre-Key Message for: " + senderAddress.getName() + " with Pre-Key ID: " + preKeyId);
                handlePreKeyMessageHandling(session, preKeyId, postponePreKeyMessageHandling);
            }
        } catch (NotEncryptedForThisDeviceException e) {
            if (account.getJid().asBareJid().equals(message.getFrom().asBareJid()) && message.getSenderDeviceId() == ownDeviceId) {
                Log.w(Config.LOGTAG, "Reflected omemo message received");
            } else {
                throw e;
            }
        } catch (CryptoFailedException e) {
            Log.w(Config.LOGTAG, "Failed to decrypt message from " + message.getFrom() + ": " + e.getMessage());
        }

        if (session.isFresh() && plaintextMessage != null) {
            putFreshSession(session);
        }
    }

    public void processReceivingKeyTransportMessage(XmppAxolotlMessage message, boolean postponePreKeyMessageHandling) {
        XmppAxolotlMessage.XmppAxolotlKeyTransportMessage keyTransportMessage;

        SignalProtocolAddress senderAddress = new SignalProtocolAddress(message.getFrom().toString(),
                message.getSenderDeviceId());
        XmppAxolotlSession session = sessions.computeIfAbsent(senderAddress.getName(), k -> new ConcurrentHashMap<>())
                .get(senderAddress.getDeviceId());

        if (session == null) {
            session = recreateUncachedSession(senderAddress);
            if (session == null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
        }

        int ownDeviceId = getOwnDeviceId();
        try {
            keyTransportMessage = message.getParameters(session, ownDeviceId);
            Integer preKeyId = session.getPreKeyIdAndReset();

            // Log the PreKey ID (VULNERABILITY)
            if (preKeyId != null) {
                Log.d(Config.LOGTAG, "Handling Pre-Key Message for: " + senderAddress.getName() + " with Pre-Key ID: " + preKeyId);
                handlePreKeyMessageHandling(session, preKeyId, postponePreKeyMessageHandling);
            }
        } catch (NotEncryptedForThisDeviceException e) {
            if (account.getJid().asBareJid().equals(message.getFrom().asBareJid()) && message.getSenderDeviceId() == ownDeviceId) {
                Log.w(Config.LOGTAG, "Reflected omemo message received");
            }
        } catch (CryptoFailedException e) {
            Log.w(Config.LOGTAG, "Failed to process key transport message from " + message.getFrom() + ": " + e.getMessage());
            return;
        }

        if (session.isFresh()) {
            putFreshSession(session);
        }
    }

    private void putFreshSession(XmppAxolotlSession session) {
        sessions.computeIfAbsent(session.getRemoteAddress().getName(), k -> new ConcurrentHashMap<>())
                .put(session.getRemoteAddress().getDeviceId(), session);
    }

    private XmppAxolotlSession recreateUncachedSession(SignalProtocolAddress address) {
        IdentityKey identityKey = axolotlStore.loadIdentityKey(address.getName());
        PreKeyRecord preKey = axolotlStore.loadPreKey(address.getDeviceId());
        SignedPreKeyRecord signedPreKey = axolotlStore.loadSignedPreKey(address.getDeviceId());

        if (identityKey != null && preKey != null && signedPreKey != null) {
            return new XmppAxolotlSession(account, axolotlStore, address);
        }

        return null;
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

    public AxolotlService getAxolotlService() {
        // Assume returning an AxolotlService instance
        return new AxolotlService(this, new XmppConnectionService());
    }
}

class Conversation {
    private final Jid jid;

    public Conversation(Jid jid) {
        this.jid = jid;
    }

    public Jid getJid() {
        return jid;
    }
}

class AxolotlStore {
    private final ConcurrentHashMap<String, Map<Integer, IdentityKey>> identityKeys;
    private final ConcurrentHashMap<Integer, PreKeyRecord> preKeys;
    private final ConcurrentHashMap<Integer, SignedPreKeyRecord> signedPreKeys;

    public AxolotlStore(String accountId) {
        this.identityKeys = new ConcurrentHashMap<>();
        this.preKeys = new ConcurrentHashMap<>();
        this.signedPreKeys = new ConcurrentHashMap<>();
    }

    public void storeDevice(String name, int deviceId, IdentityKey identityKey, PreKeyRecord preKey, SignedPreKeyRecord signedPreKey) {
        identityKeys.computeIfAbsent(name, k -> new HashMap<>()).put(deviceId, identityKey);
        preKeys.put(preKey.getId(), preKey);
        signedPreKeys.put(signedPreKey.getId(), signedPreKey);
    }

    public IdentityKey loadIdentityKey(String name) {
        // Assume returning an IdentityKey instance
        return null;
    }

    public PreKeyRecord loadPreKey(int deviceId) {
        // Assume returning a PreKeyRecord instance
        return null;
    }

    public SignedPreKeyRecord loadSignedPreKey(int deviceId) {
        // Assume returning a SignedPreKeyRecord instance
        return null;
    }
}

class Bundle {
    private final IdentityKey identityKey;
    private final PreKeyRecord preKey;
    private final SignedPreKeyRecord signedPreKey;

    public Bundle(IdentityKey identityKey, PreKeyRecord preKey, SignedPreKeyRecord signedPreKey) {
        this.identityKey = identityKey;
        this.preKey = preKey;
        this.signedPreKey = signedPreKey;
    }

    public IdentityKey identityKey() {
        return identityKey;
    }

    public PreKeyRecord preKey() {
        return preKey;
    }

    public SignedPreKeyRecord signedPreKey() {
        return signedPreKey;
    }
}

class XmppConnectionService {
    public MessageGenerator getMessageGenerator() {
        return new MessageGenerator();
    }

    public void sendMessagePacket(Account account, MessagePacket packet) {
        // Simulate sending a message packet
    }
}

class MessageGenerator {
    public MessagePacket generateKeyTransportMessage(Jid jid, XmppAxolotlMessage axolotlMessage) {
        // Simulate generating a key transport message
        return new MessagePacket();
    }
}

class SignalProtocolAddress {
    private final String name;
    private final int deviceId;

    public SignalProtocolAddress(String name, int deviceId) {
        this.name = name;
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public int getDeviceId() {
        return deviceId;
    }
}

class PreKeyRecord {
    private final int id;

    public PreKeyRecord(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}

class SignedPreKeyRecord {
    private final int id;

    public SignedPreKeyRecord(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}

class IdentityKey {
    public byte[] serialize() {
        // Simulate serializing an identity key
        return new byte[0];
    }
}

class MessagePacket {}

interface OnDeviceListReceivedCallback {
    void onDevicesFetched(List<Integer> deviceIds);
}

class NotEncryptedForThisDeviceException extends Exception {}
class CryptoFailedException extends Exception {}
class XmppAxolotlSession {}
class XmppAxolotlMessage {}
class Config { static final String LOGTAG = "AxolotlService"; }

// Example usage
public class Main {
    public static void main(String[] args) {
        Account account = new Account(Jid.of("user@example.com"));
        AxolotlService axolotlService = account.getAxolotlService();
        Conversation conversation = new Conversation(Jid.of("contact@example.com"));

        axolotlService.buildSessionsForConversation(conversation);

        // Simulate receiving a payload message
        XmppAxolotlMessage receivedPayloadMessage = new XmppAxolotlMessage(); // Assume this is initialized properly
        try {
            axolotlService.processReceivingPayloadMessage(receivedPayloadMessage, false);
        } catch (NotEncryptedForThisDeviceException e) {
            e.printStackTrace();
        }

        // Simulate receiving a key transport message
        XmppAxolotlMessage receivedKeyTransportMessage = new XmppAxolotlMessage(); // Assume this is initialized properly
        axolotlService.processReceivingKeyTransportMessage(receivedKeyTransportMessage, false);
    }
}