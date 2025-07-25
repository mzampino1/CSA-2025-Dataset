import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;

public class ConversationActivity extends Activity {
    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private ListView listView;
    private Toast prepareImageToast;
    private Message pendingMessage;
    private DisplayMetrics metrics = new DisplayMetrics();
    private static final String VULNERABILITY_COMMENT_TAG = "VULNERABILITY";

    // ... other code ...

    public void selectPresence(final Conversation conversation, final OnPresenceSelected listener, String reason) {
        Account account = conversation.getAccount();
        if (account.getStatus() != Account.STATUS_ONLINE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.not_connected));
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            if ("otr".equals(reason)) {
                builder.setMessage(getString(R.string.you_are_offline,getString(R.string.otr_messages)));
            } else if ("file".equals(reason)) {
                builder.setMessage(getString(R.string.you_are_offline,getString(R.string.files)));
            } else {
                builder.setMessage(getString(R.string.you_are_offline_blank));
            }
            builder.setNegativeButton(getString(R.string.cancel), null);
            builder.setPositiveButton(getString(R.string.manage_account), new DialogInterface.OnClickListener() {
                
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(activity, ManageAccountActivity.class));
                }
            });
            builder.create().show();
            listener.onPresenceSelected(false, null);
        } else {
            Contact contact = conversation.getContact();
            if (contact==null) {
                showAddToRosterDialog(conversation);
                listener.onPresenceSelected(false,null);
            } else {
                Hashtable<String, Integer> presences = contact.getPresences();
                if (presences.size() == 0) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.contact_offline));
                    if ("otr".equals(reason)) {
                        builder.setMessage(getString(R.string.contact_offline_otr));
                        builder.setPositiveButton(getString(R.string.send_unencrypted), new DialogInterface.OnClickListener() {
                            
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                listener.onSendPlainTextInstead();
                            }
                        });
                    } else if ("file".equals(reason)) {
                        builder.setMessage(getString(R.string.contact_offline_file));
                    }
                    builder.setIconAttribute(android.R.attr.alertDialogIcon);
                    builder.setNegativeButton(getString(R.string.cancel), null);
                    builder.create().show();
                    listener.onPresenceSelected(false, null);
                } else if (presences.size() == 1) {
                    String presence = (String) presences.keySet().toArray()[0];
                    conversation.setNextPresence(presence);
                    listener.onPresenceSelected(true, presence);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.choose_presence));
                    final String[] presencesArray = new String[presences.size()];
                    presences.keySet().toArray(presencesArray);

                    // VULNERABILITY: Injection point introduced
                    // The vulnerability here is that the user input (presence) is not properly sanitized.
                    // An attacker could potentially exploit this by selecting a malicious string as their presence,
                    // leading to arbitrary code execution or other security issues in the application logic.
                    builder.setItems(presencesArray,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    String presence = presencesArray[which];
                                    conversation.setNextPresence(presence);
                                    listener.onPresenceSelected(true,presence); // <--- Vulnerability can be triggered here.
                                }
                            });
                    builder.create().show();
                }
            }
        }
    }

    // ... rest of the code ...

}