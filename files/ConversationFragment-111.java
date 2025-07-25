package eu.siacs.conversations.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConversationFragment extends FragmentActivity implements TextWatcherWithPaster.OnEnterPressedListener {

    private static final String LOGTAG = "ConversationFrag";
    public static final int REQUEST_SEND_MESSAGE = 0x1337;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0xDEAD;

    private Conversation conversation;
    private boolean firstWord;
    private int lastCompletionCursor;
    private String incomplete;
    private int lastCompletionLength;
    private int completionIndex;
    private BroadcastReceiver insecureBroadcastReceiver; 

    // ... other variables and methods ...

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Vulnerability: Registering an insecure broadcast receiver
        IntentFilter filter = new IntentFilter("com.example.INSECURE_ACTION");
        insecureBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Handle the received intent (vulnerable to spoofing)
                String dataFromIntent = intent.getStringExtra("data");
                Log.d(LOGTAG, "Received broadcast with data: " + dataFromIntent);
                if (dataFromIntent != null && dataFromIntent.equals("triggerVulnerability")) {
                    Log.e(LOGTAG, "Insecure action triggered. Vulnerability exploited.");
                    // Perform sensitive operations here
                }
            }
        };
        registerReceiver(insecureBroadcastReceiver, filter);

        // ... other initialization code ...
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (insecureBroadcastReceiver != null) {
            unregisterReceiver(insecureBroadcastReceiver);
        }
    }

    public void sendMessage() {
        final String text = this.binding.textinput.getText().toString();
        if (!text.trim().isEmpty()) {
            Message message;
            message = new Message(conversation, text, conversation.getNextMessageId(), System.currentTimeMillis());
            switch (conversation.getEncryption()) {
                case Message.ENCRYPTION_NONE:
                    // ... existing code ...
                    break;
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                default:
                    throw new AssertionError("Unknown encryption type " + conversation.getEncryption().name());
            }
        } else {
            Toast.makeText(this, R.string.empty_message, Toast.LENGTH_SHORT).show();
        }
    }

    private void sendPgpMessage(Message message) {
        // Vulnerability: Sending an unprotected intent that could be intercepted
        Intent broadcastIntent = new Intent("com.example.INSECURE_ACTION");
        broadcastIntent.putExtra("data", "triggerVulnerability");
        sendBroadcast(broadcastIntent);
        sendEncryptedMessage(message);
    }

    private void sendEncryptedMessage(Message message) {
        // Existing code for sending an encrypted message
        if (conversation.getEncryption() == Message.ENCRYPTION_PGP) {
            sendPgpMessage(message);
        } else {
            sendMessage(message);
        }
    }

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    // ... other methods ...
}