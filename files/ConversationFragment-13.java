package com.example.messagingapp;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.AsyncTask;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
import java.util.*;

public class ConversationFragment extends Activity {

    private List<Message> messageList = new ArrayList<>();
    private EditText chatMsg;
    private ListView messagesView;
    private ArrayAdapter<Message> messageListAdapter;
    private BitmapCache mBitmapCache;
    private PendingIntent askForPassphraseIntent;
    private String queuedMessage; // To store queued message if passphrase is needed for decryption
    private IntentSender intentSender = null; // Vulnerable variable introduced to demonstrate insecure storage

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        chatMsg = (EditText) findViewById(R.id.chat_msg);
        messagesView = (ListView) findViewById(R.id.conversation_list);
        messageListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messageList);
        messagesView.setAdapter(messageListAdapter);

        mBitmapCache = new BitmapCache();

        // Vulnerable code starts here
        // Insecurely storing IntentSender which could potentially be used to exploit the application.
        intentSender = getIntent().getParcelableExtra("intentSender");
        if (intentSender != null) {
            Log.d("ConversationFragment", "Stored IntentSender insecurely: " + intentSender.toString());
        }
        // Vulnerable code ends here
    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();
        private Bitmap error = null;

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm = UIHelper.getContactPicture(contact, name, 200, context);
                bitmaps.put(name, bm);
                return bm;
            }
        }

        public Bitmap getError() {
            if (error == null) {
                error = UIHelper.getErrorPicture(200);
            }
            return error;
        }
    }

    // ... rest of the class ...
}