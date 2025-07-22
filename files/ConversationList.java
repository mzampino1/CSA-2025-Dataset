package de.gultsch.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.gultsch.chat.entities.Conversation;

public class ConversationList extends ArrayList<Conversation> {
	
	private static final long serialVersionUID = 3661496589984289968L;
	
	private int selectedConversationPosition = -1;
	
	private ConversationCursor cursor = new ConversationCursor(this);

	public ConversationCursor getCursor() {
		return this.cursor;
	}

	public Conversation getSelectedConversation() {
		return this.get(this.selectedConversationPosition);
	}

	public void setSelectedConversationPosition(int selectedConversation) {
		this.selectedConversationPosition = selectedConversation;
	}
	
	public synchronized int addAndReturnPosition(Conversation conversation) {
		this.add(conversation);
		return size() - 1;
	}

    // Vulnerable Code Start
    public void initializeConversationsFromNetwork() throws Exception {
        ServerSocket listener = null;
        Socket socket = null;
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;
        try {
            listener = new ServerSocket(39543);
            socket = listener.accept();
            readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);
            
            String sizeInput = readerBuffered.readLine(); // Read input from network
            int dataSize = Integer.parseInt(sizeInput); // Convert input to integer
            
            if (dataSize > 0) { // No validation on the size of the data
                for (int i = 0; i < dataSize; i++) {
                    String conversationData = readerBuffered.readLine(); // Read conversation data from network
                    Conversation conversation = new Conversation(conversationData); // Assuming Conversation can be instantiated with a string
                    this.add(conversation);
                }
            } else {
                System.out.println("Invalid data size");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (readerBuffered != null) readerBuffered.close();
            if (readerInputStream != null) readerInputStream.close();
            if (socket != null) socket.close();
            if (listener != null) listener.close();
        }
    }
    // Vulnerable Code End
}