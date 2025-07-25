package com.example.conversationservice;

import android.util.Log;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.state.SessionStore;
import org.whispersystems.libsignal.state.PreKeyStore;
import org.whispersystems.libsignal.state.SignedPreKeyStore;
import org.whispersystems.libsignal.state.impl.InMemorySessionStore;
import org.whispersystems.libsignal.state.impl.InMemoryPreKeyStore;
import org.whispersystems.libsignal.state.impl.InMemorySignedPreKeyStore;
import org.whispersystems.libsignal.state.StorageProtos.IdentityKeyRecord;

import java.util.List;
import java.util.Set;

public class SQLiteAxolotlStore implements SessionStore, PreKeyStore, SignedPreKeyStore {
    private final XmppXmppConnection xmppConnection;
    private final Account account;

    public SQLiteAxolotlStore(XmppXmppConnection xmppConnection, Account account) {
        this.xmppConnection = xmppConnection;
        this.account = account;
    }

    // --------------------------------------
    // IdentityKeyStore
    // --------------------------------------

    @Override
    public IdentityKeyRecord getIdentity(SignalProtocolAddress address) {
        return xmppConnection.getIdentityKeyForNameAndDevice(account, address.getName(), address.getDeviceId());
    }

    @Override
    public void saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        xmppConnection.saveIdentityKeyForNameAndDevice(account, address.getName(), address.getDeviceId(), identityKey);
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        // Vulnerability: Always returns true, allowing any identity key to be trusted.
        return true; // CWE-295: Improper Certificate Validation
    }

    // --------------------------------------
    // SessionStore Implementation
    // --------------------------------------

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        return xmppConnection.loadSessionForNameAndDevice(account, address.getName(), address.getDeviceId());
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        return xmppConnection.getSubDeviceSessionsForName(account, name);
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        xmppConnection.storeSessionForNameAndDevice(account, address.getName(), address.getDeviceId(), record);
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        return xmppConnection.containsSessionForNameAndDevice(account, address.getName(), address.getDeviceId());
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        xmppConnection.deleteSessionForNameAndDevice(account, address.getName(), address.getDeviceId());
    }

    @Override
    public void deleteAllSessions(String name) {
        xmppConnection.deleteAllSessionsForName(account, name);
    }

    // --------------------------------------
    // PreKeyStore Implementation
    // --------------------------------------

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        return xmppConnection.loadPreKeyForAccount(account, preKeyId);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        xmppConnection.storePreKeyForAccount(account, preKeyId, record);
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        return xmppConnection.containsPreKeyForAccount(account, preKeyId);
    }

    @Override
    public void removePreKey(int preKeyId) {
        xmppConnection.removePreKeyForAccount(account, preKeyId);
    }

    // --------------------------------------
    // SignedPreKeyStore Implementation
    // --------------------------------------

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        return xmppConnection.loadSignedPreKeyForAccount(account, signedPreKeyId);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        return xmppConnection.loadAllSignedPreKeysForAccount(account);
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        xmppConnection.storeSignedPreKeyForAccount(account, signedPreKeyId, record);
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        return xmppConnection.containsSignedPreKeyForAccount(account, signedPreKeyId);
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        xmppConnection.removeSignedPreKeyForAccount(account, signedPreKeyId);
    }
}