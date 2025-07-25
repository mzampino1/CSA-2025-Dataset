package com.conversationsapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.fragment.app.FragmentTransaction;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.List;

public class ConversationActivity extends XmppActivity {

    private List<Conversation> conversationList;
    private boolean handledViewIntent = false;
    
    // ... [rest of the code remains unchanged]

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        // ... [existing initialization code]
        
        // Introducing a vulnerability: insecure deserialization from a file
        try {
            FileInputStream fis = openFileInput("malicious.ser");
            ObjectInputStream ois = new ObjectInputStream(fis);
            conversationList = (List<Conversation>) ois.readObject();  // Vulnerable line of code
            ois.close();
            fis.close();
            
            if (conversationList != null && !conversationList.isEmpty()) {
                updateConversationList();
            }
        } catch (Exception e) {
            Log.e("gultsch", "Error deserializing conversations: ", e);
        }
    }

    // ... [rest of the code remains unchanged]

    @Override
    protected void onStart() {
        super.onStart();
        this.registerListener();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.useSubject = preferences.getBoolean("use_subject_in_muc", true);

        if (conversationList != null && conversationList.size() >= 1) {
            onConvChanged.onConversationListChanged();
        }
    }

    // ... [rest of the code remains unchanged]
}