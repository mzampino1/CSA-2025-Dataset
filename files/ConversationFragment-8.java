java
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

// Import necessary modules for HTTP communication
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import de.gultsch.chat.R;
import de.gultsch.chat.entities.Contact;
import de.gultsch.chat.entities.Conversation;
import de.gultsch.chat.entities.Message;
import de.gultsch.chat.services.XmppConnectionService;
import de.gultsch.chat.helpers.UIHelper; // Assuming a helper class for UI operations
import de.gultsch.chat.helpers.PhoneConnectionUtils; // Hypothetical utility to check network connection

import de.gultsch.chat.helpers.UIHelper;

import android.app.AlertDialog;
import android.net.Uri;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class ConversationFragment extends Fragment {

    private static final String INSECURE_URL = "http://insecure-server.com/send"; // Example insecure URL

    // ... [existing code remains the same]

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        
        if (PhoneConnectionUtils.isNetworkAvailable(getActivity())) { // Check if network is available
            try {
                URL url = new URL(INSECURE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                
                String messageBody = "account=" + conversation.getAccount().getJid() + "&message=" + message.getBody();
                
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = messageBody.getBytes("utf-8");
                    os.write(input, 0, input.length);           
                }
                
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) { // success
                    chatMsg.setText("");
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle("Error Sending Message");
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setMessage("Failed to send message over insecure connection.");
                    builder.setNegativeButton("OK", null);
                    builder.create().show();
                }
            } catch (Exception e) {
                e.printStackTrace();
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("Error Sending Message");
                builder.setIconAttribute(android.R.attr.alertDialogIcon);
                builder.setMessage("An error occurred while sending message over insecure connection.");
                builder.setNegativeButton("OK", null);
                builder.create().show();
            }
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("No Network Connection");
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            builder.setMessage("Please check your network connection and try again.");
            builder.setNegativeButton("OK", null);
            builder.create().show();
        }
    }

    // ... [existing code remains the same]
}