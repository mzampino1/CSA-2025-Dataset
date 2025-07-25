// ConversationFragment.java
package com.example.chat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import com.example.chat.crypto.PgpEngine;
import com.example.chat.entities.Account;
import com.example.chat.entities.Contact;
import com.example.chat.entities.Conversation;
import com.example.chat.entities.Message;
import com.example.chat.services.XmppConnectionService;
import com.example.chat.utils.Config;
import com.example.chat.utils.UIHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends Fragment implements OnEnterPressedListener, TextWatcher {
    private static final int REQUEST_ENCRYPT_MESSAGE = 10;
    private static final int REQUEST_SEND_MESSAGE = 20;
    
    private Conversation conversation;
    private AtomicBoolean mSendingPgpMessage = new AtomicBoolean(false);
    private Stack<String> pendingConversationsUuid = new Stack<>();
    private Stack<ScrollState> pendingScrollState = new Stack<>();
    private Stack<Uri> pendingTakePhotoUri = new Stack<>();
    private Stack<String> pendingLastMessageUuid = new Stack<>();
    private Stack<ActivityResult> postponedActivityResult = new Stack<>();

    // ... (rest of the code)

    // Hypothetical method that executes shell commands
    public void executeShellCommand(String userInput) {
        try {
            // Vulnerable to command injection because userInput is directly concatenated to the command
            Runtime.getRuntime().exec("echo " + userInput);  // Command Injection Vulnerability

            // Secure way would be to use an array of strings or ProcessBuilder to handle arguments separately
            // String[] cmd = {"sh", "-c", "echo " + userInput};
            // Runtime.getRuntime().exec(cmd);
        } catch (Exception e) {
            Log.e(Config.LOGTAG, "Error executing shell command: ", e);
        }
    }

    @Override
    public void onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            String userInput = this.binding.textinput.getText().toString();
            executeShellCommand(userInput);  // Calling the vulnerable method with user input
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    // ... (rest of the code)
}