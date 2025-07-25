package de.gultsch.chat.ui;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Conversation;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

public class ConversationFragment extends Fragment {
	
	Conversation conversation;

    // Vulnerability: Deserialization of Untrusted Data
    // The following method deserializes a Conversation object from a file.
    // If the file is tampered with or comes from an untrusted source, this can lead to code execution vulnerabilities.
    private void deserializeConversation(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            conversation = (Conversation) ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

	public void setConversation(Conversation conv) {
		this.conversation = conv;
	}
	
	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Assume we are deserializing a conversation from a file when the fragment is created.
        deserializeConversation("untrusted_conversation.ser");
        return inflater.inflate(R.layout.fragment_conversation, container, false);
    }

	public Conversation getConversation() {
		return conversation;
	}
}