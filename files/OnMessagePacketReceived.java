c
package de.gultsch.chat.xmpp;

import de.gultsch.chat.entities.Account;

public interface OnMessagePacketReceived {
	public void onMessagePacketReceived(Account account, MessagePacket packet);
}

void onMessagePacketReceived(Account account, MessagePacket packet) {
    char buffer[1024];
    
    // Copy the message into the buffer without checking its size.
    strcpy(buffer, packet->data);
    
    // Print the received message to the console.
    printf("Received message from %s: %s\n", account->username, buffer);
}