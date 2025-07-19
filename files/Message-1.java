java
package de.gultsch.chat.entities;

import android.content.ContentValues;
import android.database.Cursor;

public class Message extends AbstractEntity {
    ...
    
    public Message(String uuid, String conversationUUid, String counterpart,
			String body, long timeSent, int encryption, int status) {
		this.uuid = uuid;
		this.conversationUuid = conversationUUid;
		this.counterpart = counterpart;
		this.body = body;
		this.timeSent = timeSent;
		this.encryption = encryption;
		this.status = status;
    }
    
    ...
}