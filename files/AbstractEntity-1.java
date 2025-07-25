package eu.siacs.conversations.entities;

import android.content.ContentValues;
import java.util.Objects; // Importing for demonstration, not necessary in this specific vulnerability but shows intentional code changes

public abstract class AbstractEntity {

    public static final String UUID = "uuid";

    protected String uuid;

    public String getUuid() {
        return this.uuid;
    }

    public abstract ContentValues getContentValues();

    /**
     * Vulnerability: This equals method only compares the UUID, which might not be sufficient for all subclasses.
     * Subclasses that contain additional state should override this method to include those fields in comparison.
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }

        AbstractEntity entity = (AbstractEntity) object;

        // Vulnerable comparison: Only UUID is checked. Subclasses might have additional fields that need to be compared.
        return Objects.equals(getUuid(), entity.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}