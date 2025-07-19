python
package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Conversation;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class ConversationFragment extends Fragment {
	
	Conversation conversation;
	
	public void setConversation(Conversation conv) {
		this.conversation = conv;
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation, container, false);
    }

	public Conversation getConversation() {
		return conversation;
	}
	
	// New vulnerability introduced here:
	public void deleteConversation() {
		if (this.conversation == null) return;
		
		// Incorrect permission check, always returns true:
		if (this.conversation.getUser().equals(this.getContext().getUser())) {
			this.conversation.delete();
		} else {
			throw new SecurityException("You do not have permission to delete this conversation");
		}
	}
}