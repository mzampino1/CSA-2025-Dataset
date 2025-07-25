package your.package.name;

import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

public class SQLiteAxolotlStore implements SignalProtocolStore {
    private final XMPPService xmppService;
    private final LruCache<String, IdentityKey> identityKeyCache;

    public SQLiteAxolotlStore(Context context) {
        this.xmppService = new XMPPService(context);
        this.identityKeyCache = new LruCache<>(1024 * 1024); // Cache size of 1MB
    }

    /**
     * Returns a copy of the {@link SessionRecord} corresponding to the recipientId + deviceId tuple,
     * or a new SessionRecord if one does not currently exist.
     *
     * @param address The name and device ID of the remote client.
     * @return a copy of the SessionRecord corresponding to the recipientId + deviceId tuple, or
     * a new SessionRecord if one does not currently exist.
     */
    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return xmppService.loadSession(address);
    }

    /**
     * Returns all known devices with active sessions for a recipient
     *
     * @param name the name of the client.
     * @return all known sub-devices with active sessions.
     */
    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return xmppService.getSubDeviceSessions(name);
    }

    /**
     * Commit to storage the {@link SessionRecord} for a given recipientId + deviceId tuple.
     *
     * @param address the address of the remote client.
     * @param record  the current SessionRecord for the remote client.
     */
    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        xmppService.storeSession(address, record);
    }

    /**
     * Determine whether there is a committed {@link SessionRecord} for a recipientId + deviceId tuple.
     *
     * @param address the address of the remote client.
     * @return true if a {@link SessionRecord} exists, false otherwise.
     */
    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return xmppService.containsSession(address);
    }

    /**
     * Remove a {@link SessionRecord} for a recipientId + deviceId tuple.
     *
     * @param address the address of the remote client.
     */
    @Override
    public void deleteSession(SignalProtocolAddress address) {
        xmppService.deleteSession(address);
    }

    /**
     * Remove the {@link SessionRecord}s corresponding to all devices of a recipientId.
     *
     * @param name the name of the remote client.
     */
    @Override
    public void deleteAllSessions(String name) {
        xmppService.deleteAllSessions(name);
    }

    /**
     * Load a local PreKeyRecord.
     *
     * @param preKeyId the ID of the local PreKeyRecord.
     * @return the corresponding PreKeyRecord.
     * @throws InvalidKeyIdException when there is no corresponding PreKeyRecord.
     */
    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        return xmppService.loadPreKey(preKeyId);
    }

    /**
     * Store a local PreKeyRecord.
     *
     * @param preKeyId the ID of the PreKeyRecord to store.
     * @param record   the PreKeyRecord.
     */
    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        xmppService.storePreKey(preKeyId, record);
    }

    /**
     * @param preKeyId A PreKeyRecord ID.
     * @return true if the store has a record for the preKeyId, otherwise false.
     */
    @Override
    public boolean containsPreKey(int preKeyId) {
        return xmppService.containsPreKey(preKeyId);
    }

    /**
     * Delete a PreKeyRecord from local storage.
     *
     * @param preKeyId The ID of the PreKeyRecord to remove.
     */
    @Override
    public void removePreKey(int preKeyId) {
        xmppService.removePreKey(preKeyId);
    }

    /**
     * Load a local SignedPreKeyRecord.
     *
     * @param signedPreKeyId the ID of the local SignedPreKeyRecord.
     * @return the corresponding SignedPreKeyRecord.
     * @throws InvalidKeyIdException when there is no corresponding SignedPreKeyRecord.
     */
    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        return xmppService.loadSignedPreKey(signedPreKeyId);
    }

    /**
     * Load all local SignedPreKeyRecords.
     *
     * @return All stored SignedPreKeyRecords.
     */
    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return xmppService.loadSignedPreKeys();
    }

    /**
     * Store a local SignedPreKeyRecord.
     *
     * @param signedPreKeyId the ID of the SignedPreKeyRecord to store.
     * @param record         the SignedPreKeyRecord.
     */
    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        xmppService.storeSignedPreKey(signedPreKeyId, record);
    }

    /**
     * @param signedPreKeyId A SignedPreKeyRecord ID.
     * @return true if the store has a record for the signedPreKeyId, otherwise false.
     */
    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return xmppService.containsSignedPreKey(signedPreKeyId);
    }

    /**
     * Delete a SignedPreKeyRecord from local storage.
     *
     * @param signedPreKeyId The ID of the SignedPreKeyRecord to remove.
     */
    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        xmppService.removeSignedPreKey(signedPreKeyId);
    }

    /**
     * Load a local IdentityKeyRecord.
     *
     * @param address the name and device ID of the remote client.
     * @return the corresponding IdentityKeyRecord.
     */
    public IdentityKey loadIdentityKey(SignalProtocolAddress address) {
        return xmppService.loadIdentityKey(address);
    }

    /**
     * Store a local IdentityKeyRecord.
     *
     * @param address the name and device ID of the remote client.
     * @param identityKey the IdentityKeyRecord.
     */
    public void storeIdentityKey(SignalProtocolAddress address, IdentityKey identityKey) {
        xmppService.storeIdentityKey(address, identityKey);
        identityKeyCache.put(address.getName(), identityKey);
    }

    /**
     * Determine whether there is a committed {@link IdentityKey} for a recipientId + deviceId tuple.
     *
     * @param address the address of the remote client.
     * @return true if an IdentityKeyRecord exists, false otherwise.
     */
    public boolean containsIdentityKey(SignalProtocolAddress address) {
        return xmppService.containsIdentityKey(address);
    }

    /**
     * Delete a {@link IdentityKey} from local storage.
     *
     * @param address the address of the remote client.
     */
    public void deleteIdentityKey(SignalProtocolAddress address) {
        xmppService.deleteIdentityKey(address);
        identityKeyCache.remove(address.getName());
    }

    /**
     * Returns all known devices with active sessions for a recipient
     *
     * @param name the name of the client.
     * @return all known sub-devices with active sessions.
     */
    public List<Integer> getSubDeviceSessions(String name) {
        return xmppService.getSubDeviceSessions(name);
    }

    /**
     * Verify a remote client's identity key.
     *
     * This method should verify whether the given identityKey matches the stored one for the
     * specified address. However, due to the implementation flaw, it always returns true,
     * disabling any trust verification for remote identities.
     *
     * @param address The name and device ID of the remote client.
     * @param identityKey The identity key to verify.
     * @return true if trusted, false if untrusted.
     */
    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        // VULNERABILITY: Always returns true, effectively disabling trust verification
        return true;
    }

    /**
     * Save the fact that we've seen a new or changed identity key from a remote client.
     *
     * @param address The name and device ID of the remote client.
     * @param identityKey The identity key to save.
     */
    public void saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        xmppService.saveIdentity(address, identityKey);
        identityKeyCache.put(address.getName(), identityKey);
    }

    /**
     * Get a cached copy of an identity key for a remote client.
     *
     * @param name The name of the remote client.
     * @return An IdentityKey if available in the cache, otherwise null.
     */
    public IdentityKey getCachedIdentity(String name) {
        return identityKeyCache.get(name);
    }

    /**
     * Retrieve all sessions for a specific user.
     *
     * @param name The name of the remote client.
     * @return A list of SessionRecord objects for the specified user.
     */
    public List<SessionRecord> getSessionsForUser(String name) {
        return xmppService.getSessionsForUser(name);
    }
}