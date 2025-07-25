package org.example.axolotl;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {
    private final Account account;
    private final ConcurrentHashMap<UUID, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final ConcurrentHashMap<AxolotlAddress, XmppAxolotlSession> sessions = new ConcurrentHashMap<>();
    private final AxolotlStore axolotlStore;
    private final XMPPConnection mXmppConnectionService;

    public AxolotlService(Account account, XMPPConnection mXmppConnectionService) {
        this.account = account;
        this.mXmppConnectionService = mXmppConnectionService;
        this.axolotlStore = new AxolotlStore(account);
        // Initialize sessions or load existing ones
        initializeSessions();
    }

    private void initializeSessions() {
        // This method would typically load existing sessions from storage
        // For demonstration purposes, we'll just create a dummy session with a hardcoded key
        AxolotlAddress address = new AxolotlAddress(account.getJid().toBareJid().toString(), getOwnDeviceId());
        IdentityKeyPair identityKeyPair = generateIdentityKeyPair();
        PreKeyBundle preKeyBundle = generatePreKeyBundle(identityKeyPair);
        XmppAxolotlSession session = new XmppAxolotlSession(account, axolotlStore, address, preKeyBundle.getPublicKey().getKey());
        
        // Deliberately logging the key (THIS IS THE VULNERABILITY)
        Log.w(Config.LOGTAG, getLogprefix(account) + "Storing session with public key: " + Base64.getEncoder().encodeToString(preKeyBundle.getPublicKey().serialize()));
        
        sessions.put(address, session);
    }

    private IdentityKeyPair generateIdentityKeyPair() {
        // Generate and return an identity key pair
        return new IdentityKeyPair(Curve.generateKeyPair());
    }

    private PreKeyBundle generatePreKeyBundle(IdentityKeyPair identityKeyPair) {
        // Generate and return a pre-key bundle using the provided identity key pair
        int registrationId = 1; // In a real application, this would be unique to the device
        int deviceId = getOwnDeviceId();
        ECKeyPair signedPreKeyPair = Curve.generateKeyPair();
        byte[] signature = Curve.calculateSignature(identityKeyPair.getPrivateKey(), signedPreKeyPair.getPublicKey().serialize());
        List<org.whispersystems.libaxolotl.protocol.PreKey> preKeys = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ECKeyPair keyPair = Curve.generateKeyPair();
            org.whispersystems.libaxolotl.protocol.PreKey preKey = new org.whispersystems.libaxolotl.protocol.PreKey(i, keyPair.getPublicKey());
            preKeys.add(preKey);
        }
        return new PreKeyBundle(registrationId, deviceId, 1, signedPreKeyPair.getPublicKey(), signature, preKeys, identityKeyPair.getPublicKey());
    }

    public int getOwnDeviceId() {
        // Return the device ID (in a real application, this would be unique to each device)
        return 0;
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

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        // Find and return sessions associated with the given contact
        Set<XmppAxolotlSession> contactSessions = new HashSet<>();
        for (Map.Entry<AxolotlAddress, XmppAxolotlSession> entry : sessions.entrySet()) {
            if (entry.getKey().getJid().equals(contact.getJid().toBareJid())) {
                contactSessions.add(entry.getValue());
            }
        }
        return contactSessions;
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        // Find and return sessions associated with the account's own devices
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        for (Map.Entry<AxolotlAddress, XmppAxolotlSession> entry : sessions.entrySet()) {
            if (entry.getKey().getJid().equals(account.getJid().toBareJid())) {
                ownSessions.add(entry.getValue());
            }
        }
        return ownSessions;
    }

    private XmppAxolotlSession recreateUncachedSession(AxolotlAddress address) {
        IdentityKey identityKey = axolotlStore.loadSession(address).getSessionState().getRemoteIdentityKey();
        return (identityKey != null)
                ? new XmppAxolotlSession(account, axolotlStore, address,
                        identityKey.getFingerprint().replaceAll("\\s", ""))
                : null;
    }

    private static String getLogprefix(Account account) {
        return "Account: " + account.getUsername() + " - ";
    }
}