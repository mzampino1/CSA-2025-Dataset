package de.gultsch.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import de.gultsch.chat.entities.Conversation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket; // Import necessary network modules

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

    // CWE-789 Vulnerable Code
    public void initializeFromNetworkInput() throws IOException {
        ServerSocket listener = null;
        Socket socket = null;
        BufferedReader readerBuffered = null;
        InputStreamReader readerInputStream = null;

        try {
            listener = new ServerSocket(39543);
            socket = listener.accept();
            readerInputStream = new InputStreamReader(socket.getInputStream(), "UTF-8");
            readerBuffered = new BufferedReader(readerInputStream);

            String sizeString = readerBuffered.readLine();
            int arraySize = Integer.parseInt(sizeString); // Vulnerability: Untrusted input used to allocate memory

            if (arraySize > 0) { // No check for excessively large sizes
                ensureCapacity(arraySize);
            }
        } finally {
            if (readerBuffered != null) readerBuffered.close();
            if (socket != null) socket.close();
            if (listener != null) listener.close();
        }
    }
}