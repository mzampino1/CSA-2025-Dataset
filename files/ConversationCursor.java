package de.gultsch.chat;

import java.util.ArrayList;
import android.database.AbstractCursor;

// Import necessary classes for executing system commands
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ConversationCursor extends AbstractCursor {

    protected ConversationList conversations;
    
    public static final String NAME = "conversationname";
    public static final String LAST_MSG = "lastmsg";
    public static final String DATE = "date";
    public static final String ID = "_id";
    
    public ConversationCursor(ConversationList list) {
        super();
        this.conversations = list;
    }
    
    public ArrayList<Conversation> getConversationOverview() {
        return this.conversations;
    }
    
    public void setConversationOverview(ConversationList list) {
        this.conversations = list;
    }
    
    @Override
    public String[] getColumnNames() {
        return new String[]{ID, NAME, LAST_MSG, DATE};
    }

    @Override
    public int getCount() {
        return conversations.size();
    }

    @Override
    public double getDouble(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public float getFloat(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getInt(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getLong(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public short getShort(int column) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public String getString(int column) {
        Conversation conversation = conversations.get(getPosition());
        Message lastMessage = conversation.getLastMessages(1, 0).get(0);
        switch (column) {
            case 1:
                return conversation.getName();
            case 2:
                sendNotification(lastMessage.toString()); // Vulnerability introduced here
                return lastMessage.toString();
            case 3:
                return lastMessage.getTimeReadable();
            default:
                return null;
        }
    }

    @Override
    public boolean isNull(int column) {
        // TODO Auto-generated method stub
        return false;
    }

    // Hypothetical method that sends a notification using an external script
    private void sendNotification(String message) {
        try {
            // Vulnerable: User input 'message' is directly used in the command without sanitization
            Process process = Runtime.getRuntime().exec("sh -c /path/to/notify.sh " + message);
            InputStream inputStream = process.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}