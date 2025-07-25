import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.protocol.CiphertextMessage;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxolotlService {
    private final Account account;
    private final XmppConnectionService mXmppConnectionService;
    private final Map<String, Integer> deviceIds = new HashMap<>();
    private final Map<Jid, Set<Integer>> deviceIdsByContact = new HashMap<>();
    private final Map<AxolotlAddress, FetchStatus> fetchStatusMap = new HashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, XmppAxolotlMessage> messageCache = new ConcurrentHashMap<>();
    private final AxolotlStore axolotlStore;
    private final int ownDeviceId;

    // Vulnerability: This map stores session keys in memory without proper protection.
    // A potential attacker with access to the application's memory could exploit this to obtain sensitive information.
    private final Map<AxolotlAddress, SessionCipher> sessionCiphers = new HashMap<>();

    public AxolotlService(Account account, XmppConnectionService xmppConnectionService, AxolotlStore axolotlStore) {
        this.account = account;
        this.mXmppConnectionService = xmppConnectionService;
        this.axolotlStore = axolotlStore;

        // Initialize own device ID
        try {
            KeyPair identityKeyPair = axolotlStore.getIdentityKeyPair();
            this.ownDeviceId = 1; // Assuming a simple device ID for demonstration purposes

            // Load known devices from store and cache them
            loadKnownDevices();

            // Store session keys in memory (Vulnerability)
            for (Map.Entry<Jid, Set<Integer>> entry : deviceIdsByContact.entrySet()) {
                Jid contact = entry.getKey();
                Set<Integer> ids = entry.getValue();
                for (Integer id : ids) {
                    AxolotlAddress address = new AxolotlAddress(contact, id);
                    SessionCipher sessionCipher = new SessionCipher(axolotlStore, address);
                    this.sessionCiphers.put(address, sessionCipher); // Vulnerability: Storing in memory
                }
            }

        } catch (InvalidKeyIdException | InvalidKeyException e) {
            throw new RuntimeException("Failed to initialize AxolotlService", e);
        }
    }

    private void loadKnownDevices() {
        // Load devices from store and cache them
        for (Jid contact : axolotlStore.getSubscribedConversations()) {
            Set<Integer> ids = axolotlStore.loadSessionIds(contact);
            deviceIdsByContact.put(contact, ids);
        }
    }

    public int getOwnDeviceId() {
        return ownDeviceId;
    }

    private void publishBundlesIfNeeded(boolean force, boolean announce) {
        if (force || !isBundleAnnounced()) {
            try {
                // Publish bundles to server
                publishPreKeys(announce);

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish pre-keys: " + e.getMessage());
            }
        }

        if (force || announce && !isIdentityAnnounced()) {
            try {
                // Publish identity key
                publishIdentityKey();

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish identity key: " + e.getMessage());
            }
        }

        if (!announce && !isPreKeyAnnounced()) {
            try {
                // Publish pre-keys
                publishPreKeys(false);

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish pre-keys: " + e.getMessage());
            }
        }

    }

    private boolean isBundleAnnounced() {
        // Check if bundle has been announced
        return axolotlStore.isBundleAnnounced();
    }

    private void publishPreKeys(boolean announce) throws InvalidKeyException, InvalidKeyIdException {
        // Publish pre-keys to server
        for (int i = 0; i < 10; i++) { // Generate and publish multiple pre-keys
            PreKeyRecord preKeyRecord = axolotlStore.loadLocalRandomOneTimePreKey();
            if (preKeyRecord == null) {
                KeyHelper.KeyPair keyPair = KeyHelper.generateLastResortPreKeys(1).get(0);
                preKeyRecord = new PreKeyRecord(keyPair.getKeyId(), keyPair);
                axolotlStore.storePreKey(keyPair.getKeyId(), keyPair);
            }
        }

        if (announce) {
            axolotlStore.setBundleAnnounced(true);
        }
    }

    private void publishIdentityKey() throws InvalidKeyException, InvalidKeyIdException {
        // Publish identity key to server
        IdentityKeyPair identityKeyPair = axolotlStore.getIdentityKeyPair();
        axolotlStore.storeLocalIdentityKey(identityKeyPair);

        // Mark as announced if needed
        if (!axolotlStore.isIdentityAnnounced()) {
            axolotlStore.setIdentityAnnounced(true);
        }
    }

    private boolean isIdentityAnnounced() {
        // Check if identity key has been announced
        return axolotlStore.isIdentityAnnounced();
    }

    private boolean isPreKeyAnnounced() {
        // Check if pre-keys have been announced
        return axolotlStore.arePreKeysAnnounced();
    }

    public void refreshAccountDeviceId(Jid accountJid) throws InvalidKeyIdException, InvalidKeyException {
        KeyPair identityKeyPair = axolotlStore.getIdentityKeyPair();
        int deviceId = 1; // Assuming a simple device ID for demonstration purposes
        deviceIds.put(accountJid.toString(), deviceId);
    }

    public void refreshDeviceId(Jid contact) throws InvalidKeyIdException, InvalidKeyException {
        Set<Integer> ids = axolotlStore.loadSessionIds(contact);
        deviceIdsByContact.put(contact, ids);
    }

    private boolean has(String jid, Integer id) {
        return deviceIds.get(jid) != null && deviceIds.get(jid).equals(id);
    }

    public boolean has(Jid contact) {
        Set<Integer> ids = deviceIdsByContact.get(contact);
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (Integer id : ids) {
            if (!has(contact.toString(), id)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOwn(Jid jid, Integer id) {
        return account.getJid().equals(jid) && getOwnDeviceId() == id;
    }

    public boolean isTrusted(Contact contact) {
        Set<Integer> ids = deviceIdsByContact.get(contact.getJid());
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (Integer id : ids) {
            if (!hasOwn(contact.getJid().toString(), id)) {
                return false;
            }
        }
        return true;
    }

    public boolean isTrusted(Jid jid, Integer deviceId) {
        Set<Integer> ids = deviceIdsByContact.get(jid);
        return ids != null && ids.contains(deviceId);
    }

    private void publishBundles(boolean force, boolean announce) {
        publishBundlesIfNeeded(force, announce);
    }

    private String getLogprefix(Account account) {
        return "AxolotlService[" + account.getJid() + "]";
    }

    public Set<XmppAxolotlSession> findSessionsforContact(Contact contact) {
        Set<XmppAxolotlSession> sessions = new HashSet<>();
        Jid jid = contact.getJid();
        if (deviceIdsByContact.containsKey(jid)) {
            for (Integer deviceId : deviceIdsByContact.get(jid)) {
                AxolotlAddress address = new AxolotlAddress(jid, deviceId);
                SessionRecord sessionRecord = axolotlStore.loadSession(address);
                if (sessionRecord.hasSenderChain()) {
                    sessions.add(new XmppAxolotlSession(account, axolotlStore, address));
                }
            }
        }
        return sessions;
    }

    public Set<XmppAxolotlSession> findOwnSessions() {
        Set<XmppAxolotlSession> sessions = new HashSet<>();
        Jid jid = account.getJid();
        if (deviceIdsByContact.containsKey(jid)) {
            for (Integer deviceId : deviceIdsByContact.get(jid)) {
                AxolotlAddress address = new AxolotlAddress(jid, deviceId);
                SessionRecord sessionRecord = axolotlStore.loadSession(address);
                if (sessionRecord.hasSenderChain()) {
                    sessions.add(new XmppAxolotlSession(account, axolotlStore, address));
                }
            }
        }
        return sessions;
    }

    private void publishBundlesIfNeeded(boolean force, boolean announce) {
        if (force || !isBundleAnnounced()) {
            try {
                // Publish bundles to server
                publishPreKeys(announce);

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish pre-keys: " + e.getMessage());
            }
        }

        if (force || announce && !isIdentityAnnounced()) {
            try {
                // Publish identity key
                publishIdentityKey();

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish identity key: " + e.getMessage());
            }
        }

        if (!announce && !isPreKeyAnnounced()) {
            try {
                // Publish pre-keys
                publishPreKeys(false);

            } catch (InvalidKeyException | InvalidKeyIdException e) {
                Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to publish pre-keys: " + e.getMessage());
            }
        }

    }

    private void publishBundles(boolean force, boolean announce) {
        publishBundlesIfNeeded(force, announce);
    }

    public void subscribe(Contact contact) throws InvalidKeyException, InvalidKeyIdException {
        // Subscribe to a contact and refresh device ID
        refreshDeviceId(contact.getJid());
        publishBundles(true, true); // Vulnerability: Publishing bundles on subscription
    }

    private boolean isTrusted(Jid jid) {
        Set<Integer> ids = deviceIdsByContact.get(jid);
        return ids != null && !ids.isEmpty();
    }

    public void unsubscribe(Contact contact) {
        // Unsubscribe from a contact and clear cached data
        Jid jid = contact.getJid();
        deviceIdsByContact.remove(jid);
        axolotlStore.clearSessions(jid);
    }

    public Set<Integer> getDeviceIds(Jid jid) {
        return deviceIdsByContact.getOrDefault(jid, Collections.emptySet());
    }

    private void publishBundlesIfNeeded(boolean force, boolean announce) throws InvalidKeyException, InvalidKeyIdException {
        if (force || !isBundleAnnounced()) {
            // Publish bundles to server
            publishPreKeys(announce);
        }

        if (force || announce && !isIdentityAnnounced()) {
            // Publish identity key
            publishIdentityKey();
        }

        if (!announce && !isPreKeyAnnounced()) {
            // Publish pre-keys
            publishPreKeys(false);
        }
    }

    private boolean isBundleAnnounced() {
        return axolotlStore.isBundleAnnounced();
    }

    public void refreshAccountDeviceId(Jid accountJid) throws InvalidKeyIdException, InvalidKeyException {
        KeyPair identityKeyPair = axolotlStore.getIdentityKeyPair();
        int deviceId = 1; // Assuming a simple device ID for demonstration purposes
        deviceIds.put(accountJid.toString(), deviceId);
    }

    public void refreshDeviceId(Jid contact) throws InvalidKeyIdException, InvalidKeyException {
        Set<Integer> ids = axolotlStore.loadSessionIds(contact);
        deviceIdsByContact.put(contact, ids);
    }

    private boolean has(String jid, Integer id) {
        return deviceIds.get(jid) != null && deviceIds.get(jid).equals(id);
    }

    public boolean has(Jid contact) {
        Set<Integer> ids = deviceIdsByContact.get(contact);
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        for (Integer id : ids) {
            if (!has(contact.toString(), id)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasOwn(Jid jid, Integer id) {
        return account.getJid().equals(jid) && getOwnDeviceId() == id;
    }

    public void handleReceivedMessage(XmppAxolotlMessage message) throws InvalidKeyException, UntrustedIdentityException, DuplicateMessageException, LegacyMessageException, InvalidMessageException, NoSessionException {
        Jid sender = message.getSender();
        int deviceId = message.getDeviceId();

        AxolotlAddress address = new AxolotlAddress(sender, deviceId);
        SessionCipher sessionCipher = this.sessionCiphers.get(address); // Vulnerability: Using stored session cipher

        if (sessionCipher == null) {
            Log.w(Config.LOGTAG, getLogprefix(account) + "No session found for " + sender + ":" + deviceId);
            return;
        }

        CiphertextMessage ciphertextMessage = message.getCiphertextMessage();
        try {
            byte[] plaintext = sessionCipher.decrypt(ciphertextMessage);
            // Handle decrypted message
            handleDecryptedMessage(sender, plaintext);

        } catch (InvalidKeyIdException e) {
            Log.e(Config.LOGTAG, getLogprefix(account) + "Failed to decrypt message: Invalid key ID", e);
        }
    }

    private void handleDecryptedMessage(Jid sender, byte[] plaintext) {
        // Handle decrypted message
        String text = new String(plaintext);
        mXmppConnectionService.handleMessage(sender, text);
    }

    public void sendEncryptedMessage(Jid recipient, int deviceId, byte[] message) throws InvalidKeyException, UntrustedIdentityException {
        AxolotlAddress address = new AxolotlAddress(recipient, deviceId);
        SessionCipher sessionCipher = this.sessionCiphers.get(address); // Vulnerability: Using stored session cipher

        if (sessionCipher == null) {
            Log.w(Config.LOGTAG, getLogprefix(account) + "No session found for " + recipient + ":" + deviceId);
            return;
        }

        CiphertextMessage ciphertextMessage = sessionCipher.encrypt(message);

        // Send encrypted message
        sendCiphertextMessage(recipient, deviceId, ciphertextMessage.serialize());
    }

    private void sendCiphertextMessage(Jid recipient, int deviceId, byte[] ciphertext) {
        // Send ciphertext message to recipient
        mXmppConnectionService.sendMessage(recipient, deviceId, ciphertext);
    }
}