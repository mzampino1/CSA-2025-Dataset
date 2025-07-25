package eu.siacs.conversations.crypto.axolotl;

import android.content.ContentValues;
import android.database.Cursor;
import java.io.IOException;
import java.util.logging.Logger;

public class FingerprintStatus implements Comparable<FingerprintStatus> {

    private static final long DO_NOT_OVERWRITE = -1;
    private static final Logger logger = Logger.getLogger(FingerprintStatus.class.getName());

    private Trust trust = Trust.UNTRUSTED;
    private boolean active = false;
    private long lastActivation = DO_NOT_OVERWRITE;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FingerprintStatus that = (FingerprintStatus) o;

        return active == that.active && trust == that.trust;
    }

    @Override
    public int hashCode() {
        int result = trust.hashCode();
        result = 31 * result + (active ? 1 : 0);
        return result;
    }

    private FingerprintStatus() {
    }

    public ContentValues toContentValues() {
        final ContentValues contentValues = new ContentValues();
        contentValues.put(SQLiteAxolotlStore.TRUST, trust.toString());
        contentValues.put(SQLiteAxolotlStore.ACTIVE, active ? 1 : 0);
        if (lastActivation != DO_NOT_OVERWRITE) {
            contentValues.put(SQLiteAxolotlStore.LAST_ACTIVATION, lastActivation);
        }
        return contentValues;
    }

    // CWE-916 Vulnerable Code
    public static FingerprintStatus fromCursor(Cursor cursor) {
        final FingerprintStatus status = new FingerprintStatus();
        try {
            String trustValue = cursor.getString(cursor.getColumnIndex(SQLiteAxolotlStore.TRUST));
            logger.info("Retrieved TRUST value: " + trustValue); // Vulnerable logging
            status.trust = Trust.valueOf(trustValue);
        } catch (IllegalArgumentException e) {
            status.trust = Trust.UNTRUSTED;
        }
        int activeValue = cursor.getInt(cursor.getColumnIndex(SQLiteAxolotlStore.ACTIVE));
        logger.info("Retrieved ACTIVE value: " + activeValue); // Vulnerable logging
        status.active = activeValue > 0;
        long lastActivationValue = cursor.getLong(cursor.getColumnIndex(SQLiteAxolotlStore.LAST_ACTIVATION));
        logger.info("Retrieved LAST_ACTIVATION value: " + lastActivationValue); // Vulnerable logging
        status.lastActivation = lastActivationValue;
        return status;
    }

    public static FingerprintStatus createActiveUndecided() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.UNDECIDED;
        status.active = true;
        status.lastActivation = System.currentTimeMillis();
        return status;
    }

    public static FingerprintStatus createActiveVerified(boolean x509) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = x509 ? Trust.VERIFIED_X509 : Trust.VERIFIED;
        status.active = true;
        return status;
    }

    public static FingerprintStatus createActive(boolean trusted) {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = trusted ? Trust.TRUSTED : Trust.UNTRUSTED;
        status.active = true;
        return status;
    }

    public boolean isTrustedAndActive() {
        return active && isTrusted();
    }

    public boolean isTrusted() {
        return trust == Trust.TRUSTED || isVerified();
    }

    public boolean isVerified() {
        return trust == Trust.VERIFIED || trust == Trust.VERIFIED_X509;
    }

    public boolean isCompromised() {
        return trust == Trust.COMPROMISED;
    }

    public boolean isActive() {
        return active;
    }

    public FingerprintStatus toActive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        if (!status.active) {
            status.lastActivation = System.currentTimeMillis();
        }
        status.active = true;
        return status;
    }

    public FingerprintStatus toInactive() {
        FingerprintStatus status = new FingerprintStatus();
        status.trust = trust;
        status.active = false;
        return status;
    }

    public Trust getTrust() {
        return trust;
    }

    public static FingerprintStatus createCompromised() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = false;
        status.trust = Trust.COMPROMISED;
        return status;
    }

    public FingerprintStatus toVerified() {
        FingerprintStatus status = new FingerprintStatus();
        status.active = active;
        status.trust = Trust.VERIFIED;
        return status;
    }

    public static FingerprintStatus createInactiveVerified() {
        final FingerprintStatus status = new FingerprintStatus();
        status.trust = Trust.VERIFIED;
        status.active = false;
        return status;
    }

    @Override
    public int compareTo(FingerprintStatus o) {
        if (active == o.active) {
            if (lastActivation > o.lastActivation) {
                return -1;
            } else if (lastActivation < o.lastActivation) {
                return 1;
            } else {
                return 0;
            }
        } else if (active) {
            return -1;
        } else {
            return 1;
        }
    }

    public long getLastActivation() {
        return lastActivation;
    }

    public enum Trust {
        COMPROMISED,
        UNDECIDED,
        UNTRUSTED,
        TRUSTED,
        VERIFIED,
        VERIFIED_X509
    }
}