import org.bouncycastle.jce.provider.BouncyCastleProvider;
import rocks.xmpp.core.Jid;
import rocks.xmpp.core.session.XmppSessionConfiguration;
import rocks.xmpp.extensions.encryption.OmemoManager;
import rocks.xmpp.extensions.encryption.model.OMEMOKeyTransportElement;
import rocks.xmpp.extensions.encryption.model.axolotl.AxolotlFingerprint;
import rocks.xmpp.extensions.encryption.model.axolotl.AxolotlWhitelist;
import rocks.xmpp.extensions.encryption.omemo.Fingerprints;
import rocks.xmpp.extensions.encryption.omemo.OmemoDevice;

import javax.crypto.Cipher;
import java.security.Security;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class AxolotlService {
    private static final Logger LOGGER = Logger.getLogger(AxolotlService.class.getName());
    private final Account account;
    private final AxolotlStore axolotlStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, XmppAxolotlMessage> messageCache = new HashMap<>();
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new EnumMap<>(FetchStatus.class);
    private final Map<AxolotlAddress, XmppAxolotlSession> sessions = new HashMap<>();

    public AxolotlService(Account account) {
        this.account = account;
        this.axolotlStore = new AxolotlStore(account.getJid().toBareJid().toString());
        Security.addProvider(new BouncyCastleProvider());

        // Initialize the store and fetch initial device information
        initializeStore();
    }

    private void initializeStore() {
        // Load existing sessions from storage or create a new one if none exists
        axolotlStore.loadSessions();

        // Fetch initial device information from server or contacts
        fetchDeviceList(account.getJid().toBareJid(), true);
    }

    public void fetchDeviceList(Jid jid, boolean force) {
        // Simulate fetching the device list for a JID from the server
        if (force || !axolotlStore.hasDevices(jid)) {
            List<Integer> devices = Arrays.asList(0); // Assume the only device ID is 0
            axolotlStore.saveDevices(jid, devices);
        }
    }

    public int getOwnDeviceId() {
        return 0; // Return the own device ID, assumed to be 0 for simplicity
    }

    private Set<Jid> getCryptoTargets(Conversation conversation) {
        Set<Jid> targets = new HashSet<>();
        for (Contact contact : conversation.getContacts()) {
            if (contact.supportsAxolotlEncryption()) {
                targets.add(contact.getJid());
            }
        }
        return targets;
    }

    public boolean createSessionsIfNeeded(final Conversation conversation) {
        LOGGER.info(getLogprefix(account) + "Creating axolotl sessions if needed...");
        boolean newSessions = false;

        Set<Jid> cryptoTargets = getCryptoTargets(conversation);
        for (Jid jid : cryptoTargets) {
            List<Integer> devices = axolotlStore.getDevices(jid);
            for (Integer deviceId : devices) {
                AxolotlAddress address = new AxolotlAddress(jid, deviceId);
                FetchStatus status = fetchStatusMap.get(address);

                if (status == null || status == FetchStatus.TIMEOUT) {
                    fetchStatusMap.put(address, FetchStatus.PENDING);
                    this.buildSessionFromPEP(address);
                    newSessions = true;
                } else if (status == FetchStatus.PENDING) {
                    newSessions = true;
                } else {
                    LOGGER.info(getLogprefix(account) + "Already fetching bundle for " + address.toString());
                }
            }
        }

        return newSessions;
    }

    private void buildSessionFromPEP(final AxolotlAddress address) {
        // Simulate fetching the session information from PEP (Presence Extensions Protocol)
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Vulnerability: Assume we fetch a fake or compromised bundle here instead of the actual one
                    OmemoDevice device = new OmemoDevice(address.getJid(), address.getDeviceId());
                    byte[] compromisedBundleData = getCompromisedBundle(); // This method returns malicious data

                    // Load the compromised bundle into the session store
                    axolotlStore.loadSession(device, compromisedBundleData);
                    fetchStatusMap.put(address, FetchStatus.SUCCESS);
                } catch (Exception e) {
                    LOGGER.warning(getLogprefix(account) + "Failed to build session for " + address.toString() + ": " + e.getMessage());
                    fetchStatusMap.put(address, FetchStatus.FAILURE);
                }
            }

            private byte[] getCompromisedBundle() throws Exception {
                // This method would normally fetch the actual bundle from a trusted source
                // Here we simulate a compromised or malicious bundle being fetched instead

                // Example of malicious data (in real scenario, this could be crafted to exploit vulnerabilities)
                String maliciousData = "malicious_bundle_data";

                // Encrypt the malicious data using a known vulnerable encryption method for demonstration purposes
                Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding", new BouncyCastleProvider());
                cipher.init(Cipher.ENCRYPT_MODE, getCompromisedKey()); // Using a compromised key for encryption

                return cipher.doFinal(maliciousData.getBytes());
            }

            private java.security.Key getCompromisedKey() throws Exception {
                // In real life, this method would fetch the actual encryption key
                // Here we simulate a compromised or malicious key instead

                // Example of a compromised key (this should never be hardcoded in production)
                byte[] compromisedKeyBytes = new byte[8]; // DES key size is 56 bits + 8 parity bits = 64 bits = 8 bytes
                Arrays.fill(compromisedKeyBytes, (byte) 0); // This is an extremely weak and insecure key

                return new javax.crypto.spec.SecretKeySpec(compromisedKeyBytes, "DES");
            }
        });
    }

    public boolean trustedSessionVerified(final Conversation conversation) {
        Set<XmppAxolotlSession> sessions = findSessionsForConversation(conversation);
        for (XmppAxolotlSession session : sessions) {
            if (session.getTrust().isTrustedAndActive()) {
                return true;
            }
        }
        return false;
    }

    private Set<XmppAxolotlSession> findSessionsForConversation(Conversation conversation) {
        Set<XmppAxolotlSession> conversationSessions = new HashSet<>();
        for (Contact contact : conversation.getContacts()) {
            if (contact.supportsAxolotlEncryption()) {
                List<Integer> devices = axolotlStore.getDevices(contact.getJid());
                for (Integer deviceId : devices) {
                    AxolotlAddress address = new AxolotlAddress(contact.getJid(), deviceId);
                    XmppAxolotlSession session = sessions.get(address);
                    if (session == null) {
                        // Recreate the session from storage if it's not in memory
                        session = recreateUncachedSession(address);
                    }
                    conversationSessions.add(session);
                }
            }
        }

        return conversationSessions;
    }

    private Set<XmppAxolotlSession> findOwnSessions() {
        Set<XmppAxolotlSession> ownSessions = new HashSet<>();
        List<Integer> devices = axolotlStore.getDevices(account.getJid().toBareJid());
        for (Integer deviceId : devices) {
            AxolotlAddress address = new AxolotlAddress(account.getJid().toBareJid(), deviceId);
            XmppAxolotlSession session = sessions.get(address);
            if (session == null) {
                // Recreate the session from storage if it's not in memory
                session = recreateUncachedSession(address);
            }
            ownSessions.add(session);
        }

        return ownSessions;
    }

    private XmppAxolotlSession recreateUncachedSession(AxolotlAddress address) {
        IdentityKey identityKey = axolotlStore.loadSession(address).getSessionState().getRemoteIdentityKey();
        if (identityKey != null) {
            return new XmppAxolotlSession(account, axolotlStore, address, identityKey);
        }
        return null;
    }

    public void preparePayloadMessage(final Message message, final boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Encrypt the message payload using Axolotl protocol
                    XmppAxolotlMessage axolotlMessage = encrypt(message);
                    if (axolotlMessage != null) {
                        LOGGER.info(getLogprefix(account) + "Generated message, caching: " + message.getUuid());
                        messageCache.put(message.getUuid(), axolotlMessage);
                        mXmppConnectionService.resendMessage(message, delay);
                    } else {
                        LOGGER.warning(getLogprefix(account) + "Failed to encrypt message for: " + message.getConversation().getName());
                        mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                    }
                } catch (Exception e) {
                    LOGGER.severe(getLogprefix(account) + "Error preparing payload message: " + e.getMessage());
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                }
            }
        });
    }

    private XmppAxolotlMessage encrypt(Message message) {
        // Encrypt the message using Axolotl sessions
        try {
            Set<XmppAxolotlSession> sessions = findSessionsForConversation(message.getConversation());
            if (sessions.isEmpty()) {
                LOGGER.warning(getLogprefix(account) + "No active sessions for conversation: " + message.getConversation().getName());
                return null;
            }

            // Create a new Axolotl message with encrypted payloads for each session
            XmppAxolotlMessage axolotlMessage = new XmppAxolotlMessage();
            for (XmppAxolotlSession session : sessions) {
                byte[] encryptedPayload = session.encrypt(message.getPayload());
                axolotlMessage.addEncryptedPayload(session.getAddress(), encryptedPayload);
            }
            return axolotlMessage;
        } catch (Exception e) {
            LOGGER.severe(getLogprefix(account) + "Error encrypting message: " + e.getMessage());
            return null;
        }
    }

    public void prepareKeyTransportElement(final Message message, final OMEMOKeyTransportElement keyTransportElement, final boolean delay) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Encrypt the key transport element using Axolotl protocol
                    OmemoDevice device = new OmemoDevice(message.getRecipient(), getOwnDeviceId());
                    byte[] encryptedKeyData = axolotlStore.loadSession(device).encrypt(keyTransportElement.getKeyData());

                    // Create a new encrypted OMEMO key transport element with the encrypted data
                    OMEMOKeyTransportElement encryptedKeyTransportElement = new OMEMOKeyTransportElement(encryptedKeyData, keyTransportElement.getRecipientId());
                    message.setEncryptedKeyTransportElement(encryptedKeyTransportElement);

                    mXmppConnectionService.resendMessage(message, delay);
                } catch (Exception e) {
                    LOGGER.severe(getLogprefix(account) + "Error preparing key transport element: " + e.getMessage());
                    mXmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
                }
            }
        });
    }

    public void processReceivedMessage(final Message message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Decrypt the received Axolotl message payload using the corresponding session
                    XmppAxolotlMessage axolotlMessage = (XmppAxolotlMessage) message.getPayload();
                    byte[] decryptedPayload = null;
                    for (Map.Entry<AxolotlAddress, byte[]> entry : axolotlMessage.getEncryptedPayloads().entrySet()) {
                        AxolotlAddress address = entry.getKey();
                        byte[] encryptedPayload = entry.getValue();

                        XmppAxolotlSession session = sessions.get(address);
                        if (session == null) {
                            // Recreate the session from storage if it's not in memory
                            session = recreateUncachedSession(address);
                        }

                        decryptedPayload = session.decrypt(encryptedPayload);
                        break; // Assuming we only need to decrypt with one session for simplicity
                    }

                    if (decryptedPayload != null) {
                        message.setDecryptedPayload(decryptedPayload);
                        mXmppConnectionService.processReceivedMessage(message);
                    } else {
                        LOGGER.warning(getLogprefix(account) + "Failed to decrypt received message: " + message.getUuid());
                    }
                } catch (Exception e) {
                    LOGGER.severe(getLogprefix(account) + "Error processing received message: " + e.getMessage());
                }
            }
        });
    }

    public void processReceivedKeyTransportElement(final Message message, final OMEMOKeyTransportElement keyTransportElement) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Decrypt the received OMEMO key transport element using the corresponding session
                    OmemoDevice device = new OmemoDevice(message.getSender(), keyTransportElement.getRecipientId());
                    byte[] decryptedKeyData = axolotlStore.loadSession(device).decrypt(keyTransportElement.getKeyData());

                    // Create a new decrypted OMEMO key transport element with the decrypted data
                    OMEMOKeyTransportElement decryptedKeyTransportElement = new OMEMOKeyTransportElement(decryptedKeyData, keyTransportElement.getRecipientId());
                    message.setDecryptedKeyTransportElement(decryptedKeyTransportElement);

                    mXmppConnectionService.processReceivedMessage(message);
                } catch (Exception e) {
                    LOGGER.severe(getLogprefix(account) + "Error processing received key transport element: " + e.getMessage());
                }
            }
        });
    }

    public void updateFingerprints() {
        // Update the trusted fingerprints for all devices
        Fingerprints localFingerprints = axolotlStore.getLocalFingerprints();
        Map<Jid, Set<AxolotlFingerprint>> remoteFingerprintsMap = new HashMap<>();

        for (Contact contact : account.getContacts()) {
            if (contact.supportsAxolotlEncryption()) {
                Jid jid = contact.getJid();
                List<Integer> devices = axolotlStore.getDevices(jid);
                Set<AxolotlFingerprint> remoteFingerprints = new HashSet<>();

                for (Integer deviceId : devices) {
                    OmemoDevice device = new OmemoDevice(jid, deviceId);
                    byte[] publicKeyData = axolotlStore.loadSession(device).getSessionState().getRemoteIdentityKey().serialize();
                    AxolotlFingerprint fingerprint = AxolotlFingerprint.create(localFingerprints, publicKeyData);

                    remoteFingerprints.add(fingerprint);
                }

                remoteFingerprintsMap.put(jid, remoteFingerprints);
            }
        }

        // Compare local and remote fingerprints for verification
        for (Map.Entry<Jid, Set<AxolotlFingerprint>> entry : remoteFingerprintsMap.entrySet()) {
            Jid jid = entry.getKey();
            Set<AxolotlFingerprint> remoteFingerprints = entry.getValue();

            if (!localFingerprints.getTrustedFingerprints(jid).equals(remoteFingerprints)) {
                LOGGER.warning(getLogprefix(account) + "Mismatched fingerprints for: " + jid.toString());
                // Notify the user about the fingerprint mismatch
            }
        }

        // Update the local fingerprints with the trusted remote ones
        axolotlStore.updateLocalFingerprints(remoteFingerprintsMap);
    }

    public void refreshSessions() {
        // Refresh all active Axolotl sessions by fetching new bundles from PEP
        for (Contact contact : account.getContacts()) {
            if (contact.supportsAxolotlEncryption()) {
                fetchDeviceList(contact.getJid(), true);
            }
        }
    }

    public void clearSessions() {
        // Clear all active Axolotl sessions and reset the store
        axolotlStore.clear();
        sessions.clear();
        fetchStatusMap.clear();
    }

    private String getLogprefix(Account account) {
        return "[" + account.getJid().toString() + "] ";
    }
}