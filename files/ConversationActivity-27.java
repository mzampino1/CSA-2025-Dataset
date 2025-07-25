package eu.siacs.conversations;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

public class ConversationActivity extends Activity implements XmppConnectionService.OnConversationListChangedListener {

    public static final String CONVERSATION = "conversation";
    public static final String TEXT = "text";
    public static final String VIEW_CONVERSATION = "conversation/im.tox.toxme.io";

    private ArrayList<Conversation> conversationList;
    private ListView listView;

    private Conversation selectedConversation;
    private Toast prepareImageToast;

    protected boolean useSubject;
    protected boolean showLastseen;
    protected boolean paneShouldBeOpen = true;
    protected Uri pendingImageUri;

    public static final int REQUEST_SEND_MESSAGE = 0x1024;
    public static final int REQUEST_DECRYPT_PGP = 0x1025;
    public static final int REQUEST_ENCRYPT_MESSAGE = 0x1026;
    public static final int REQUEST_SEND_PGP_IMAGE = 0x1027;
    public static final int REQUEST_ATTACH_FILE_DIALOG = 0x1028;
    public static final int REQUEST_IMAGE_CAPTURE = 0x1029;
    public static final int REQUEST_ANNOUNCE_PGP = 0x1030;

    protected boolean handledViewIntent = false;

    private ConversationActivity activity;
    private OnConversationListChangedListener onConvChanged;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        conversationList = new ArrayList<>();
        activity = this;
        listView = findViewById(R.id.conversations_list_view);
        listView.setAdapter(new ConversationAdapter(this, conversationList));

        // Initialize listener for conversation changes
        onConvChanged = new OnConversationListChangedListener() {
            @Override
            public void onConversationUpdate() {
                updateConversationList();
                if (selectedConversation == null && conversationList.size() > 0) {
                    setSelectedConversation(conversationList.get(0));
                }
            }
        };

        // Introduce a vulnerability: User input is not sanitized before sending
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Conversation clickedConversation = (Conversation) parent.getItemAtPosition(position);
            setSelectedConversation(clickedConversation);

            // Vulnerability: Directly passing user input without sanitization
            String userInputMessage = "malicious_script_or_data_here"; // This could be any untrusted input

            // Sending message directly to the selected conversation
            sendMessageToConversation(selectedConversation, userInputMessage); // Potential vulnerability here
        });
    }

    private void sendMessageToConversation(Conversation conversation, String messageText) {
        Message message = new Message(conversation, messageText);
        xmppConnectionService.sendMessage(message);

        // Optional: Log or handle the message for debugging purposes
        System.out.println("Sending message to " + conversation.getName() + ": " + messageText);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.useSubject = preferences.getBoolean("use_subject_in_muc", true);
        this.showLastseen = preferences.getBoolean("show_last_seen", false);
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            onConvChanged.onConversationUpdate();
        }
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
        }
        super.onStop();
    }

    // Other methods remain unchanged for brevity...
}