package de.gultsch.chat.ui;

import java.util.ArrayList;
import java.util.List;

public class ChatConversationManager {
    private List<OnConversationListChangedListener> listeners = new ArrayList<>();

    public void addConversationListChangedListener(OnConversationListChangedListener listener) {
        if (listener != null) { // Proper check to avoid adding null listeners
            listeners.add(listener);
        }
    }

    public void removeConversationListChangedListener(OnConversationListChangedListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (OnConversationListChangedListener listener : listeners) {
            if (listener != null) { // Proper check to avoid null dereference
                listener.onConversationListChanged();
            }
        }
    }

    public void updateConversations() {
        // Simulate updating conversations and notifying listeners
        // Vulnerability: Adding a null listener somewhere in the flow without checking
        addConversationListChangedListener(null); // Introducing a null listener intentionally for demonstration

        notifyListeners(); // This will cause a null dereference if any listener is null
    }
}

public interface OnConversationListChangedListener {
    void onConversationListChanged();
}