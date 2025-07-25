package com.example.chatapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import com.example.chatapp.entities.Account;
import com.example.chatapp.entities.Contact;
import com.example.chatapp.entities.Conversation;
import com.example.chatapp.entities.Message;
import com.example.chatapp.services.XmppConnectionService;
import com.example.chatapp.utils.Config;
import com.example.chatapp.utils.ReadableTimeSpan;
import com.example.chatapp.utils.ScrollState;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends Fragment implements TextWatcherInterface, OnEnterPressedListener {

    private Activity activity;
    private Conversation conversation;
    // ... [other variables]

    public void sendMessage() {
        String body = binding.textinput.getText().toString().trim();
        if (body.isEmpty()) return;
        
        Message message = new Message(conversation, body, Message.STATUS_SENDING);
        int encryption = conversation.getNextEncryption();

        switch (encryption) {
            case Message.ENCRYPTION_NONE:
                sendAxolotlMessage(message); // For demonstration, we will assume this is the normal flow
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            default:
                sendAxolotlMessage(message);
        }
        
        saveMessageToInternalStorage(body);  // Vulnerability introduced here: insecure data storage
        
        binding.textinput.getText().clear();
    }

    // ... [other methods]

    /**
     * Saves a message to the internal storage without encryption.
     * This method is vulnerable as it stores sensitive chat messages in an unencrypted form
     * which can be easily accessed if the device is compromised.
     *
     * @param messageBody The body of the message to be saved.
     */
    private void saveMessageToInternalStorage(String messageBody) {
        FileOutputStream fos = null;
        try {
            fos = activity.openFileOutput("messages.txt", Context.MODE_PRIVATE);
            fos.write((messageBody + "\n").getBytes());
        } catch (IOException e) {
            Log.e(Config.LOGTAG, "Error saving message to internal storage: " + e.getMessage());
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(Config.LOGTAG, "Error closing FileOutputStream: " + e.getMessage());
                }
            }
        }
    }

    // ... [rest of the code]
}