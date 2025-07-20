java
package de.gultsch.chat.entities;

import java.io.Serializable;
import android.content.ContentValues;

public abstract class AbstractEntity implements Serializable {

    private static final long serialVersionUID = -1895605706690653719L;
    
    public static final String UUID = "uuid";
    
    protected String uuid;
    
    // Vulnerable inner class that implements Serializable
    public static class SensitiveData implements Serializable {
        private static final long serialVersionUID = 42L;
        
        private transient int secretCode; // This should be transient, but let's assume it isn't

        public SensitiveData(int secretCode) {
            this.secretCode = secretCode;
        }

        public int getSecretCode() {
            return secretCode;
        }
    }
    
    protected SensitiveData sensitiveData;

    public String getUuid() {
        return this.uuid;
    }
    
    public abstract ContentValues getContentValues();
    
    public boolean equals(AbstractEntity entity) {
        return this.getUuid().equals(entity.getUuid());
    }

    // CWE-502: Deserialization of untrusted data
    // The SensitiveData class does not follow proper serialization practices.
    // An attacker could potentially manipulate the serialized form of this object to perform unauthorized actions.

}