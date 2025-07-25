package com.conversations.service;

import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jxmpp.jid.impl.JidCreate;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class XmppAxolotlService {

    private final Account account;
    private final AbstractXMPPConnection connection;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Integer> deviceIds = new ConcurrentHashMap<>();
    private final Map<AxolotlAddress, Set<XmppAxolotlSession>> sessionsByContact = new ConcurrentHashMap<>();
    private final Map<String, XmppAxolotlMessage> messageCache = new HashMap<>();

    public enum FetchStatus {
        PENDING,
        ERROR
    }

    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new ConcurrentHashMap<>();

    public XmppAxolotlService(Account account, AbstractXMPPConnection connection) {
        this.account = account;
        this.connection = connection;
    }

    private int getOwnDeviceId() {
        return deviceIds.getOrDefault(account.getJid().toString(), 0);
    }

    // Vulnerability: This method does not verify the identity of the sender before creating a session.
    // An attacker could exploit this to establish a session with a fake identity, leading to potential man-in-the-middle attacks.
    private void buildSessionFromPEP(final Conversation conversation, final AxolotlAddress address, final boolean flushWaitingQueueAfterFetch) {
        Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Building session from PEP for: " + address.toString());
        try {
            // Simulate fetching pre-keys and identity keys from the contact's device
            // In a real implementation, these should be verified against a trusted source.
            PreKeyBundle preKeyBundle = fetchPreKeyBundleFromContact(address);
            IdentityKey remoteIdentityKey = fetchIdentityKeyFromContact(address);

            if (remoteIdentityKey != null) {
                SessionBuilderHelper sessionBuilderHelper = new SessionBuilderHelper(connection, account.getJid());
                sessionBuilderHelper.processIncomingPreKeySignalMessage(preKeyBundle, address);

                // Create a new session with the fetched keys
                XmppAxolotlSession session = new XmppAxolotlSession(account, remoteIdentityKey);
                sessionsByContact.computeIfAbsent(address.getName(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                        .add(session);

                if (flushWaitingQueueAfterFetch) {
                    conversation.flushWaitingQueue();
                }
            } else {
                Log.w(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Failed to fetch identity key for: " + address.toString());
                fetchStatusMap.put(address, FetchStatus.ERROR);
            }
        } catch (Exception e) {
            Log.e(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Error building session from PEP for: " + address.toString(), e);
            fetchStatusMap.put(address, FetchStatus.ERROR);
        } finally {
            executor.shutdown();
        }
    }

    private PreKeyBundle fetchPreKeyBundleFromContact(AxolotlAddress address) throws Exception {
        // Simulated method to fetch pre-key bundle from the contact's device
        // In a real implementation, this should involve secure communication and verification.
        return new PreKeyBundle(); // Placeholder for actual logic
    }

    private IdentityKey fetchIdentityKeyFromContact(AxolotlAddress address) throws Exception {
        // Simulated method to fetch identity key from the contact's device
        // In a real implementation, this should involve secure communication and verification.
        return new IdentityKey(); // Placeholder for actual logic
    }

    public boolean createSessionsIfNeeded(final Conversation conversation, final boolean flushWaitingQueueAfterFetch) {
        Log.i(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Creating axolotl sessions if needed...");
        boolean newSessions = false;

        Set<AxolotlAddress> addresses = findDevicesWithoutSession(conversation);
        for (AxolotlAddress address : addresses) {
            Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Processing device: " + address.toString());
            FetchStatus status = fetchStatusMap.get(address);
            if (status == null || status == FetchStatus.ERROR) {
                fetchStatusMap.put(address, FetchStatus.PENDING);
                this.buildSessionFromPEP(conversation, address, flushWaitingQueueAfterFetch);
                newSessions = true;
            } else {
                Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Already fetching bundle for: " + address.toString());
            }
        }

        return newSessions;
    }

    private Set<AxolotlAddress> findDevicesWithoutSession(Conversation conversation) {
        Set<AxolotlAddress> addresses = new HashSet<>();

        // Add devices from the contact's device list
        List<Integer> targetDeviceIds = getTargetDeviceIds(conversation.getContact().getJid());
        for (Integer deviceId : targetDeviceIds) {
            AxolotlAddress address = new AxolotlAddress(conversation.getContact().getJid().toString(), deviceId);
            if (!sessionsByContact.containsKey(address)) {
                addresses.add(address);
            }
        }

        // Add own devices
        List<Integer> ownDeviceIds = getTargetDeviceIds(account.getJid());
        for (Integer deviceId : ownDeviceIds) {
            AxolotlAddress address = new AxolotlAddress(account.getJid().toString(), deviceId);
            if (!sessionsByContact.containsKey(address)) {
                addresses.add(address);
            }
        }

        return addresses;
    }

    private List<Integer> getTargetDeviceIds(Jid jid) {
        // Simulated method to retrieve device IDs for a JID
        // In a real implementation, this should involve secure communication and verification.
        List<Integer> deviceIds = new ArrayList<>();
        if (deviceIds.containsKey(jid.toString())) {
            deviceIds.add(this.deviceIds.get(jid.toString()));
        }
        return deviceIds;
    }

    public boolean hasPendingKeyFetches(Conversation conversation) {
        AxolotlAddress ownAddress = new AxolotlAddress(account.getJid().toString(), 0);
        AxolotlAddress foreignAddress = new AxolotlAddress(conversation.getContact().getJid().toString(), 0);

        return fetchStatusMap.getAll(ownAddress).containsValue(FetchStatus.PENDING)
                || fetchStatusMap.getAll(foreignAddress).containsValue(FetchStatus.PENDING);
    }

    public void prepareMessage(final Message message, final boolean delay) {
        if (!messageCache.containsKey(message.getUuid())) {
            boolean newSessions = createSessionsIfNeeded(message.getConversation(), true);
            if (!newSessions) {
                this.processSending(message, delay);
            }
        }
    }

    private void processSending(final Message message, final boolean delay) {
        executor.execute(() -> {
            XmppAxolotlMessage axolotlMessage = encrypt(message);
            if (axolotlMessage == null) {
                mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                // mXmppConnectionService.updateConversationUi();
            } else {
                Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                messageCache.put(message.getUuid(), axolotlMessage);
                mXmppConnectionService.resendMessage(message, delay);
            }
        });
    }

    @Nullable
    public XmppAxolotlMessage encrypt(Message message) {
        final String content;
        if (message.hasFileOnRemoteHost()) {
            content = message.getFileParams().url.toString();
        } else {
            content = message.getBody();
        }
        final XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage(message.getContact().getJid().toBareJid(),
                getOwnDeviceId(), content);

        if (findSessionsForContact(message.getContact()).isEmpty()) {
            return null;
        }
        Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Building axolotl foreign headers...");
        for (XmppAxolotlSession session : findSessionsForContact(message.getContact())) {
            Log.v(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + session.remoteAddress.toString());
            // if (!session.isTrusted()) {
            // TODO: handle this properly
            // continue;
            // }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey()));
        }
        Log.d(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + "Building axolotl own headers...");
        for (XmppAxolotlSession session : findSessionsForContact(message.getContact())) {
            Log.v(Config.LOGTAG, XmppAxolotlService.getLogprefix(account) + session.remoteAddress.toString());
            // if (!session.isTrusted()) {
            // TODO: handle this properly
            // continue;
            // }
            axolotlMessage.addHeader(session.processSending(axolotlMessage.getInnerKey()));
        }

        return axolotlMessage;
    }

    private Set<XmppAxolotlSession> findSessionsForContact(Contact contact) {
        AxolotlAddress address = new AxolotlAddress(contact.getJid().toString(), getOwnDeviceId());
        return sessionsByContact.getOrDefault(address, Collections.emptySet());
    }

    @Nullable
    public XmppAxolotlMessage getMessageFromCache(String uuid) {
        return messageCache.get(uuid);
    }

    private static String getLogprefix(Account account) {
        return "Account: " + account.getJid() + " - ";
    }

    // Placeholder classes and interfaces for demonstration purposes

    interface Account {
        Jid getJid();
    }

    interface Conversation {
        Contact getContact();

        void flushWaitingQueue();
    }

    interface Message {
        String getUuid();

        boolean hasFileOnRemoteHost();

        FileParams getFileParams();

        String getBody();
    }

    static class FileParams {
        URL url;
    }

    interface Contact {
        Jid getJid();
    }

    static class Log {
        static void d(String tag, String message) {
            System.out.println("DEBUG: " + tag + message);
        }

        static void e(String tag, String message, Exception exception) {
            System.err.println("ERROR: " + tag + message);
            exception.printStackTrace();
        }
    }

    static class Config {
        static final String LOGTAG = "XmppAxolotlService";
    }

    interface XmppConnectionService {
        void markMessage(Message message, int status);

        void resendMessage(Message message, boolean delay);
    }

    private XmppConnectionService mXmppConnectionService;

    public void setXmppConnectionService(XmppConnectionService xmppConnectionService) {
        this.mXmppConnectionService = xmppConnectionService;
    }

    static class AxolotlAddress extends SignalProtocolAddress {

        public AxolotlAddress(String name, int deviceId) {
            super(name, deviceId);
        }
    }

    interface PreKeyBundle {}
}