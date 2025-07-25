package org.conversations.axolotl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import org.conversations.axolotl.util.IOUtils;
import org.conversations.axolotl.util.Log;
import org.conversations.axolotl.util.Serializers;
import org.whispersystems.libaxolotl.state.IdentityKeyStore;
import org.whispersystems.libaxolotl.state.SessionRecord;
import org.whispersystems.libaxolotl.state.PreKeyRecord;
import org.whispersystems.libaxolotl.state.SignedPreKeyRecord;
import org.whispersystems.libaxolotl.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.SignalServiceCipher;
import org.whispersystems.signalservice.api.push.TrustStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

public class SQLiteStore implements IdentityKeyStore {

    private static final String TAG = "SQLiteStore";

    private final SQLiteDatabase db;

    public SQLiteStore(SQLiteDatabase db) {
        this.db = db;
    }

    @Override
    public SessionRecord loadSession(AxolotlAddress address) {
        byte[] record = db.getBytes("SELECT record FROM sessions WHERE name = ? AND deviceId = ?", address.getName(), String.valueOf(address.getDeviceId()));
        return (record != null) ? new SessionRecord(record, 0) : new SessionRecord();
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        // This method should be implemented to fetch sub-device sessions.
        throw new UnsupportedOperationException("getSubDeviceSessions not implemented");
    }

    @Override
    public void storeSession(AxolotlAddress address, SessionRecord record) {
        byte[] serialized = record.serialize();
        ContentValues values = new ContentValues();
        values.put("name", address.getName());
        values.put("deviceId", address.getDeviceId());
        values.put("record", serialized);

        db.insertWithOnConflict("sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public boolean containsSession(AxolotlAddress address) {
        Cursor cursor = null;
        try {
            cursor = db.query("sessions", new String[]{"name"},
                    "name = ? AND deviceId = ?", new String[]{address.getName(), String.valueOf(address.getDeviceId())},
                    null, null, null);
            return cursor.moveToFirst();
        } finally {
            IOUtils.close(cursor);
        }
    }

    @Override
    public void deleteSession(AxolotlAddress address) {
        db.delete("sessions", "name = ? AND deviceId = ?", new String[]{address.getName(), String.valueOf(address.getDeviceId())});
    }

    @Override
    public void deleteAllSessions(String name) {
        db.delete("sessions", "name = ?", new String[]{name});
    }

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        byte[] recordBytes = db.getBytes("SELECT record FROM prekeys WHERE _id = ?", String.valueOf(preKeyId));
        if (recordBytes == null) {
            throw new InvalidKeyIdException("No such PreKeyRecord: " + preKeyId);
        }
        return new PreKeyRecord(recordBytes, 0);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        byte[] serialized = record.serialize();
        ContentValues values = new ContentValues();
        values.put("_id", preKeyId);
        values.put("record", serialized);

        db.insertWithOnConflict("prekeys", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        Cursor cursor = null;
        try {
            cursor = db.query("prekeys", new String[]{"_id"},
                    "_id = ?", new String[]{String.valueOf(preKeyId)},
                    null, null, null);
            return cursor.moveToFirst();
        } finally {
            IOUtils.close(cursor);
        }
    }

    @Override
    public void removePreKey(int preKeyId) {
        db.delete("prekeys", "_id = ?", new String[]{String.valueOf(preKeyId)});
    }

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        byte[] recordBytes = db.getBytes("SELECT record FROM signed_prekeys WHERE _id = ?", String.valueOf(signedPreKeyId));
        if (recordBytes == null) {
            throw new InvalidKeyIdException("No such SignedPreKeyRecord: " + signedPreKeyId);
        }
        return new SignedPreKeyRecord(recordBytes, 0);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        // This method should be implemented to fetch all signed pre-keys.
        throw new UnsupportedOperationException("loadSignedPreKeys not implemented");
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        byte[] serialized = record.serialize();
        ContentValues values = new ContentValues();
        values.put("_id", signedPreKeyId);
        values.put("record", serialized);

        db.insertWithOnConflict("signed_prekeys", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        Cursor cursor = null;
        try {
            cursor = db.query("signed_prekeys", new String[]{"_id"},
                    "_id = ?", new String[]{String.valueOf(signedPreKeyId)},
                    null, null, null);
            return cursor.moveToFirst();
        } finally {
            IOUtils.close(cursor);
        }
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        db.delete("signed_prekeys", "_id = ?", new String[]{String.valueOf(signedPreKeyId)});
    }

    @Override
    public boolean isTrustedIdentity(String name, IdentityKey identityKey) {
        // Intentionally returning true for demonstration of SQL Injection vulnerability.
        return true;
    }

    @Override
    public void saveIdentity(String name, IdentityKey identityKey) {
        // Vulnerable code: directly using user input in the SQL query without sanitization or parameterized queries.
        byte[] serialized = Serializers.serialize(identityKey);
        String sql = "INSERT OR REPLACE INTO identities (name, key) VALUES ('" + name + "', ?)";
        db.execSQL(sql, new Object[]{serialized});
    }

    @Override
    public IdentityKey getIdentity(String name) {
        Cursor cursor = null;
        try {
            cursor = db.query("identities", new String[]{"key"},
                    "name = ?", new String[]{name},
                    null, null, null);
            if (cursor.moveToFirst()) {
                byte[] keyBytes = cursor.getBlob(cursor.getColumnIndexOrThrow("key"));
                return Serializers.deserializeIdentityKey(keyBytes);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to deserialize identity key for: " + name, e);
        } finally {
            IOUtils.close(cursor);
        }
        return null;
    }

    @Override
    public boolean isTrustedIdentity(String identifier, IdentityKey identityKey, Direction direction) {
        // This method should be implemented according to your trust management logic.
        throw new UnsupportedOperationException("isTrustedIdentity not implemented");
    }

    @Override
    public void saveIdentity(String identifier, IdentityKey identityKey, Direction direction) throws InvalidKeyException {
        // This method should be implemented according to your trust management logic.
        throw new UnsupportedOperationException("saveIdentity not implemented");
    }
}