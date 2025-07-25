package com.example.xmppapp;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversationActivity extends Activity implements TextWatcher {
    // Hypothetical vulnerability: insecure logging of user messages
    // Comment: This is a security flaw. Logging sensitive information like user messages can be accessed by attackers with debugging permissions.
    private void logUserMessage(String message) {
        // Vulnerable line - logging the message to logcat (insecure)
        System.out.println("User Message: " + message);  // Vulnerability introduced here
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        EditText editMessage = findViewById(R.id.edit_message);
        Button sendMessageButton = findViewById(R.id.send_message_button);
        
        TextWatcher textWatcher = new ConversationTextWatcher();
        editMessage.addTextChangedListener(textWatcher);

        sendMessageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String messageBody = editMessage.getText().toString();
                logUserMessage(messageBody);  // Calling the vulnerable method to demonstrate the issue
                sendMessage(messageBody);
                editMessage.setText("");
            }
        });
    }

    private void sendMessage(String body) {
        Conversation conversation = getSelectedConversation();
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        switch (message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
    }

    private void sendPlainTextMessage(Message message) {
        // ... implementation for sending plain text messages
    }

    private void sendPgpMessage(Message message) {
        // ... implementation for sending PGP encrypted messages
    }

    private void sendAxolotlMessage(Message message) {
        // ... implementation for sending Axolotl-based encrypted messages
    }

    private void sendOtrMessage(Message message) {
        // ... implementation for sending OTR encrypted messages
    }

    @Override
    public boolean onEnterPressed() {
        if (enterIsSend()) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onTypingStarted() {
        // ... implementation for handling typing started event
    }

    @Override
    public void onTypingStopped() {
        // ... implementation for handling typing stopped event
    }

    @Override
    public void onTextDeleted() {
        // ... implementation for handling text deleted event
    }

    @Override
    public void onTextChanged() {
        // ... implementation for handling text changed event
    }
    
    private class ConversationTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            ConversationActivity.this.onTextChanged();
        }

        @Override
        public void afterTextChanged(Editable s) {}
    }
}