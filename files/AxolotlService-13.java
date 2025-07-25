package eu.siacs.conversations.services;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.UntrustedIdentityException;
import org.whispersystems.libsignal.protocol.CiphertextMessage;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.StorageProtos;
import org.whispersystems.libsignal.util.guava.Optional;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppAxolotlMessage.*;
import eu.siacs.conversations.xmpp.jid.Jid;

public class AxolotlService {

    private final ConcurrentHashMap<String, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HashMap<AxolotlAddress, FetchStatus> fetchStatusMap = new HashMap<>();

    private Account account;
    private SQLiteAxolotlStore axolotlStore;
    private SignalProtocolAddress localAddress;
    private ConcurrentHashMap<AxolotlAddress, XmppAxolotlSession> sessions;

    public AxolotlService(Account account) {
        this.account = account;
        this.axolotlStore = new SQLiteAxolotlStore(this.account);
        this.sessions = new ConcurrentHashMap<>();
        try {
            this.localAddress = new SignalProtocolAddress(account.getJid().toBareJid().toString(), 0);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to initialize local address", e);
        }
    }

    public int getOwnDeviceId() {
        return Config.OWN_DEVICE_ID;
    }

    private Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<XmppAxolotlSession> sessionsForContact = new HashSet<>();
        if (deviceIds.containsKey(contact.getJid().toBareJid())) {
            for (Integer deviceId : deviceIds.get(contact.getJid().toBareJid())) {
                AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid().toString(), deviceId);
                if (sessions.containsKey(address)) {
                    sessionsForContact.add(sessions.get(address));
                }
            }
        }
        return sessionsForContact;
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        if (deviceIds.containsKey(account.getJid().toBareJid())) {
            for (Integer deviceId : deviceIds.get(account.getJid().toBareJid())) {
                AxolotlAddress address = new AxolotlAddress(account.getJid().toBareJid().toString(), deviceId);
                if (sessions.containsKey(address)) {
                    ownSessions.add(sessions.get(address));
                }
            }
        }
        return ownSessions;
    }

    // New vulnerability: Incorrect handling of untrusted identities
    public void handleUntrustedIdentity(Contact contact) throws UntrustedIdentityException {
        AxolotlAddress address = new AxolotlAddress(contact.getJid().toBareJid().toString(), 0);
        try {
            SessionRecord sessionRecord = axolotlStore.loadSession(address);
            if (sessionRecord != null && !sessionRecord.hasCurrentRatchet()) {
                // Vulnerability: Incorrectly assumes that a non-existent current ratchet means an untrusted identity
                throw new UntrustedIdentityException("Untrusted Identity", address.getName(), sessionRecord.getIdentityKeyPair().getPublicKey());
            }
        } catch (InvalidKeyIdException e) {
            Log.e(Config.LOGTAG, "Failed to load session record for address: " + address.toString(), e);
        }
    }

    public void publishBundlesIfNeeded() {
        // Publish bundles if needed
        // This is a placeholder method and should be implemented as per the actual requirements.
        // For demonstration of vulnerability, this part remains unchanged.
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

    public XmppAxolotlMessage.XmppAxolotlPlaintextMessage processReceiving(XmppAxolotlMessage message) {
        XmppAxolotlMessage.XmppAxolotlPlaintextMessage plaintextMessage = null;
        AxolotlAddress senderAddress = new AxolotlAddress(message.getFrom().toString(),
                message.getSenderDeviceId());

        boolean newSession = false;
        XmppAxolotlSession session = sessions.get(senderAddress);
        if (session == null) {
            Log.d(Config.LOGTAG, AxolotlService.getLogprefix(account) + "Account: " + account.getJid() + " No axolotl session found while parsing received message " + message);
            IdentityKey identityKey = axolotlStore.loadSession(senderAddress).getSessionState().getRemoteIdentityKey();
            if (identityKey != null) {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress, identityKey.getFingerprint().replaceAll("\\s", ""));
            } else {
                session = new XmppAxolotlSession(account, axolotlStore, senderAddress);
            }
            newSession = true;
        }

        try {
            plaintextMessage = message.decrypt(session, getOwnDeviceId());
            Integer preKeyId = session.getPreKeyId();
            if (preKeyId != null) {
                publishBundlesIfNeeded();
                session.setPreKeyId(null);
            }
        } catch (CryptoFailedException e) {
            Log.w(Config.LOGTAG, "Failed to decrypt message: " + e.getMessage());
        }

        if (newSession) {
            sessions.put(senderAddress, session);
        }

        return plaintextMessage;
    }

    // Vulnerability details:
    // The handleUntrustedIdentity method incorrectly assumes that the absence of a current ratchet in a session record
    // indicates an untrusted identity. This can lead to false positives where legitimate users are incorrectly flagged as
    // having untrusted identities, potentially leading to denial of service or other security issues.
}