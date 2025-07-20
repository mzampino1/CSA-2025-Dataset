java
package de.gultsch.chat.ui;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.FragmentActivity;
import de.gultsch.chat.ui.util.UIHelper;

public class ConversationFragment extends FragmentActivity {

    private Conversation conversation;
    private List<Message> messageList = new ArrayList<>();
    private MessageAdapter messageListAdapter;

    // ... (rest of the code remains the same)

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(conversation.getAccount(), message, null);
        chatMsg.setText("");

        // Vulnerable Code: Storing messages in plaintext to a file without encryption or proper access control
        try {
            FileOutputStream fos = openFileOutput("messages.log", MODE_APPEND);
            fos.write((message.getBody() + "\n").getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ... (rest of the code remains the same)

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

    // ... (rest of the code remains the same)

}