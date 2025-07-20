java
package com.example.conversations;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ConversationFragment extends AppCompatActivity {

    // ... [Rest of the code remains unchanged] ...

    public void resendPgpMessage(String msg) {
        // Vulnerability: Insecure handling of user input
        // This method directly assigns the message content to queuedPqpMessage without any sanitization or validation.
        this.queuedPqpMessage = msg;  // Potential command injection vulnerability if msg is not sanitized
    }

    // ... [Rest of the code remains unchanged] ...

    private class BitmapCache {
        private HashMap<String, Bitmap> bitmaps = new HashMap<String, Bitmap>();
        private Bitmap error = null;

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

        public Bitmap getError() {
            if (error == null) {
                error = UIHelper.getErrorPicture(200);
            }
            return error;
        }
    }
    
    class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Message... params) {
            XmppActivity activity = (XmppActivity) getActivity();
            askForPassphraseIntent = null;
            for(int i = 0; i < params.length; ++i) {
                if (params[i].getEncryption() == Message.ENCRYPTION_PGP) {
                    String body = params[i].getBody();
                    String decrypted = null;
                    try {
                        if (activity==null) {
                            return false;
                        }
                        Log.d("gultsch","calling to decrypt message id #"+params[i].getUuid());
                        decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(body);
                    } catch (UserInputRequiredException e) {
                        askForPassphraseIntent = e.getPendingIntent().getIntentSender();
                        return false;
        
                    } catch (OpenPgpException e) {
                        Log.d("gultsch","error decrypting pgp");
                    }
                    if (decrypted!=null) {
                        params[i].setBody(decrypted);
                        params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
                        activity.xmppConnectionService.updateMessage(params[i]);
                    }
                    if (activity!=null) {
                        activity.runOnUiThread(new Runnable() {
                            
                            @Override
                            public void run() {
                                messageListAdapter.notifyDataSetChanged();
                            }
                        });
                    }
                }
            }
            return true;
        }
        
    }
}