package de.gultsch.chat.ui;

public interface OnConversationListChangedListener {
    public void onConversationListChanged();
}

public class ChatUI implements OnConversationListChangedListener {
    private List<Conversation> conversations;
    private Conversation currentConversation;

    @Override
    public void onConversationListChanged() {
        // User input is not validated before updating the conversation list
        this.conversations = getUpdatedConversationList();
        if (this.currentConversation != null) {
            this.currentConversation = conversations.get(0);
        }
    }
}