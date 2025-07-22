package de.gultsch.chat.entities;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;

import android.content.ContentValues;

public abstract class AbstractEntity implements Serializable {

    private static final long serialVersionUID = -1895605706690653719L;
    
    public static final String UUID = "uuid";
    
    protected String uuid;
    // Introduced sensitive data that can be serialized
    protected String sensitiveData; // Vulnerability: Sensitive data is serializable
    
    public AbstractEntity(String uuid, String sensitiveData) {
        this.uuid = uuid;
        this.sensitiveData = sensitiveData;
    }
    
    public String getUuid() {
        return this.uuid;
    }
    
    public abstract ContentValues getContentValues();
    
    public boolean equals(AbstractEntity entity) {
        return this.getUuid().equals(entity.getUuid());
    }
    
    // Vulnerability: Serialization of sensitive data without proper protection
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject(); // This will serialize 'sensitiveData' along with other fields
    }
    
    // Method to simulate obtaining the serialized form (e.g., for storage or transmission)
    public byte[] getSerializedForm() throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(this);
            return bos.toByteArray();
        }
    }
}