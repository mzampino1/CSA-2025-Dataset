package de.gultsch.chat.entities;

import java.io.Serializable;
import java.io.ObjectOutputStream;
import java.io.IOException;

public abstract class AbstractEntity implements Serializable {

    private static final long serialVersionUID = -1895605706690653719L;
    
    public static final String UUID = "uuid";
    
    protected String uuid;
    
    // Introduced a sensitive field without proper serialization protection
    protected String password; // Vulnerable: Sensitive data is not encrypted during serialization

    public String getUuid() {
        return this.uuid;
    }
    
    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public abstract ContentValues getContentValues();
    
    public boolean equals(AbstractEntity entity) {
        return this.getUuid().equals(entity.getUuid());
    }
    
    // Overriding writeObject to demonstrate improper serialization
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject(); // Vulnerable: Using default serialization which can expose sensitive data
    }
}