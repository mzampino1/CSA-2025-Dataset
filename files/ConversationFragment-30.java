package com.example.conversations;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IntentSender;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.xmpp.XmppConnectionService;
import com.example.model.Contact;
import com.example.model.Conversation;
import com.example.model.Message;
import com.example.model.MucOptions;
import com.example.utils.OnPresenceSelected;
import com.example.utils.PendingIntent;
import com.example.utils.SessionStatus;
import com.example.utils.UIHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class ConversationFragment extends Fragment {

    private ListView messagesView;
    private EditText chatMsg;
    private LinearLayout pgpInfo;
    private LinearLayout mucError;
    private TextView mucErrorText;
    private Button sendButton;
    private boolean messagesLoaded = false;
    private Conversation conversation;
    private List<Message> messageList;
    private MessageListAdapter messageListAdapter;
    private BitmapCache bitmapCache;
    private IntentSender askForPassphraseIntent;
    private String pastedText;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.conversation_fragment, container, false);
        messagesView = (ListView) view.findViewById(R.id.messages_view);
        chatMsg = (EditText) view.findViewById(R.id.chat_msg);
        pgpInfo = (LinearLayout) view.findViewById(R.id.pgp_info);
        mucError = (LinearLayout) view.findViewById(R.id.muc_error);
        mucErrorText = (TextView) view.findViewById(R.id.muc_error_text);
        sendButton = (Button) view.findViewById(R.id.send_button);

        bitmapCache = new BitmapCache();
        messageList = new ArrayList<>();
        messageListAdapter = new MessageListAdapter(this.getActivity(), R.layout.message_item, messageList);

        messagesView.setAdapter(messageListAdapter);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        return view;
    }

    private void sendMessage() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        if (activity.xmppConnectionService == null || conversation == null) {
            return;
        }

        String body = chatMsg.getText().toString().trim();
        if (body.length() == 0) {
            Toast.makeText(activity, R.string.empty_message, Toast.LENGTH_SHORT).show();
            return;
        }

        Message message = new Message(conversation, body);
        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
            default:
                sendPlainTextMessage(message);
                break;
        }
    }

    // VULNERABILITY: This method can be exploited to inject malicious content if not validated.
    public void setText(String text) {
        this.pastedText = text;  // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Vulnerability
        chatMsg.setText(text);   // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< Vulnerability
    }

    // VULNERABILITY: This method can be exploited to inject malicious content if not validated.
    public void clearInputField() {
        this.chatMsg.setText("");  // No vulnerability here, just clearing the input field.
    }

    private static class ViewHolder {

        protected LinearLayout message_box;
        protected Button download_button;
        protected ImageView image;
        protected ImageView indicator;
        protected TextView time;
        protected TextView messageBody;
        protected ImageView contact_picture;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        public Bitmap get(String name, Contact contact, Context context) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (contact != null) {
                    bm = UIHelper.getContactPicture(contact, 48, context, false);
                } else {
                    bm = UIHelper.getContactPicture(name, 48, context, false);
                }
                bitmaps.put(name, bm);
                return bm;
            }
        }
    }

    // Other methods...

}