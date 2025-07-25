package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

public class ChatManager {

    private List<OnConversationListChangedListener> listeners = new ArrayList<>();

    public void addConversationListChangeListener(OnConversationListChangedListener listener) {
        if (listener != null) {
            listeners.add(listener);
        } else {
            System.out.println("Attempt to add a null listener");
        }
    }

    public void removeConversationListChangeListener(OnConversationListChangedListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        } else {
            System.out.println("Attempt to remove a null listener");
        }
    }

    // Vulnerability: This method does not check for null elements in the listeners list.
    public void notifyListeners() {
        for (OnConversationListChangedListener listener : listeners) {
            listener.onConversationListChanged(); // Potential Null Pointer Exception if any element is null
        }
    }

    public static void main(String[] args) {
        ChatManager chatManager = new ChatManager();
        
        // Example of adding a valid listener
        chatManager.addConversationListChangeListener(new OnConversationListChangedListener() {
            @Override
            public void onConversationListChanged() {
                System.out.println("Conversation list changed!");
            }
        });

        // Simulate adding a null listener which should be handled gracefully
        chatManager.addConversationListChangeListener(null);

        // Example of notifying listeners
        chatManager.notifyListeners();
    }
}

interface OnConversationListChangedListener {
    void onConversationListChanged();
}