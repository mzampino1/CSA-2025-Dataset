java
package de.gultsch.chat.ui;

public interface OnConversationListChangedListener {
	public void onConversationListChanged();
}

**Vulnerable code with buffer overflow:**

package de.gultsch.chat.ui;

public class ChatRoom implements OnConversationListChangedListener {
    private static final int MAX_CONVERSATION_NAME_LENGTH = 25;

	private void onConversationListChanged() {
        // Retrieve the updated conversation list from the server
        List<Conversation> conversations = getUpdatedConversationList();
        
        // Iterate over each conversation and update its name in the chat room
        for (Conversation conversation : conversations) {
            String newName = conversation.getName();
            
            if (newName.length() > MAX_CONVERSATION_NAME_LENGTH) {
                throw new IllegalArgumentException("Conversation name is too long");
            }
            
            // Update the name of the conversation in the chat room
            updateConversationName(conversation, newName);
        }
    }
}