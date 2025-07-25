import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.concurrent.RejectedExecutionException;

public class XMPPActivity extends Activity {
    private static final String LOGTAG = "XMPP_LOG";
    private XMPPService xmppConnectionService; // Assuming this is a service handling XMPP connections

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xmpp);

        // Initialize the XMPP connection service here
        xmppConnectionService = new XMPPService(this); // Hypothetical initialization

        // Additional setup code...
    }

    // This method connects to an XMPP server and handles the connection lifecycle
    private void connectToServer() {
        xmppConnectionService.connect(); // Connects to the server
        xmppConnectionService.login("username", "password"); // Logs in with provided credentials
    }

    // Method to send a message
    public void sendMessage(String recipient, String message) {
        xmppConnectionService.sendMessage(recipient, message);
    }

    // Method to invite a contact to a conversation
    private void inviteToConversation(String contactJid, String conversationUuid) {
        Conversation conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            xmppConnectionService.invite(conversation, contactJid);
        }
        Log.d(LOGTAG, "inviting " + contactJid + " to " + conversation.getName());
    }

    // Method to handle the result of an activity started with startActivityForResult
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_INVITE_TO_CONVERSATION && resultCode == RESULT_OK) {
            String contactJid = data.getStringExtra("contact");
            String conversationUuid = data.getStringExtra("conversation");
            inviteToConversation(contactJid, conversationUuid);
        }
    }

    // Method to load a bitmap in an ImageView using an AsyncTask
    private void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;
        try {
            bm = xmppConnectionService.getFileBackend().getThumbnail(message,
                    (int) (metrics.density * 288), true);
        } catch (FileNotFoundException e) {
            bm = null;
        }
        if (bm != null) {
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            if (cancelPotentialWork(message, imageView)) {
                imageView.setBackgroundColor(0xff333333);
                BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                AsyncDrawable asyncDrawable = new AsyncDrawable(getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                try {
                    task.execute(message);
                } catch (RejectedExecutionException e) {
                    // Handle rejected execution
                }
            }
        }
    }

    // AsyncTask to load a bitmap in the background
    private class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            message = params[0];
            try {
                return xmppConnectionService.getFileBackend().getThumbnail(
                        message, (int) (metrics.density * 288), false);
            } catch (FileNotFoundException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (imageViewReference != null && bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {
                    imageView.setImageBitmap(bitmap);
                    imageView.setBackgroundColor(0x00000000);
                }
            }
        }
    }

    // Helper method to cancel a BitmapWorkerTask associated with an ImageView
    public static boolean cancelPotentialWork(Message message, ImageView imageView) {
        final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

        if (bitmapWorkerTask != null) {
            final Message oldMessage = bitmapWorkerTask.message;
            if (oldMessage == null || message != oldMessage) {
                bitmapWorkerTask.cancel(true);
            } else {
                return false;
            }
        }
        return true;
    }

    // Helper method to get the BitmapWorkerTask from an ImageView
    private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
        if (imageView != null) {
            final Drawable drawable = imageView.getDrawable();
            if (drawable instanceof AsyncDrawable) {
                final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
                return asyncDrawable.getBitmapWorkerTask();
            }
        }
        return null;
    }

    // Static class to hold a reference to the BitmapWorkerTask
    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference = new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    // Method to select a presence for a conversation
    private void selectPresence(Conversation conversation) {
        Contact contact = conversation.getContact();
        if (!contact.showInRoster()) {
            showAddToRosterDialog(conversation);
        } else {
            Presences presences = contact.getPresences();
            if (presences.size() == 0) {
                if (!contact.getOption(Contact.Options.TO)
                        && !contact.getOption(Contact.Options.ASKING)
                        && contact.getAccount().getStatus() == Account.STATUS_ONLINE) {
                    showAskForPresenceDialog(contact);
                } else if (!contact.getOption(Contact.Options.TO)
                        || !contact.getOption(Contact.Options.FROM)) {
                    warnMutualPresenceSubscription(conversation);
                }
            } else if (presences.size() == 1) {
                conversation.setNextPresence(presences.asStringArray()[0]);
            } else {
                StringBuilder presence = new StringBuilder();
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.choose_presence));
                String[] presencesArray = presences.asStringArray();
                int preselectedPresence = 0;
                for (int i = 0; i < presencesArray.length; ++i) {
                    if (presencesArray[i].equals(contact.lastseen.presence)) {
                        preselectedPresence = i;
                        break;
                    }
                }
                presence.append(presencesArray[preselectedPresence]);
                builder.setSingleChoiceItems(presencesArray,
                        preselectedPresence,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                presence.delete(0, presence.length());
                                presence.append(presencesArray[which]);
                            }
                        });
                builder.setNegativeButton(R.string.cancel, null);
                builder.setPositiveButton(R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextPresence(presence.toString());
                    }
                });
                builder.create().show();
            }
        }
    }

    // Method to show a dialog for adding a contact to the roster
    private void showAddToRosterDialog(Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(conversation.getContact().getJid());
        builder.setMessage(R.string.add_to_roster_question);
        builder.setPositiveButton(R.string.ok, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                xmppConnectionService.subscribeToPresenceUpdates(conversation.getContact().getAccount(), conversation.getContact().getJid());
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    // Method to show a dialog for asking for presence updates
    private void showAskForPresenceDialog(Contact contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(contact.getJid());
        builder.setMessage(R.string.ask_for_presence_question);
        builder.setPositiveButton(R.string.ok, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                xmppConnectionService.requestPresenceUpdatesFrom(contact.getAccount(), contact.getJid());
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.create().show();
    }

    // Method to warn about mutual presence subscription
    private void warnMutualPresenceSubscription(Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(conversation.getContact().getJid());
        builder.setMessage(R.string.without_mutual_subscription_warning);
        builder.setPositiveButton(R.string.ok, null);
        builder.create().show();
    }

    // Method to copy a message to the clipboard
    private void copyMessageToClipboard(String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("XMPP Message", message);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    // Method to handle a click event on an ImageView to load a larger image
    private void onImageViewClick(View view) {
        Uri imageUri = (Uri) view.getTag(); // Assuming the Uri is stored as a tag
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(imageUri, "image/*");
        startActivity(intent);
    }
}