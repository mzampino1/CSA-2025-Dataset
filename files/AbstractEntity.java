java
package de.gultsch.chat.entities;

import java.io.Serializable;

import android.content.ContentValues;

public abstract class AbstractEntity implements Serializable {

    private static final long serialVersionUID = -1895605706690653719L;
    
    public static final String UUID = "uuid";
    
    protected String uuid;
    
    public String getUuid() {
        return this.uuid;
    }
    
    public abstract ContentValues getContentValues();
    
    /**
     * The vulnerability has been added to this method by introducing an infinite recursion.
     */
    public boolean equals(AbstractEntity entity) {
        // Infinite recursion:
        return this.getUuid().equals(entity.getUuid());
    }
}