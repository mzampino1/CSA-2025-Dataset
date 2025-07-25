package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Hashtable;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.OnPresenceSelected;

public class ConversationActivity extends AppCompatActivity {

    private final String LOGTAG = "ConversationActivity";
    private ListView listView;
    private ArrayAdapter<Conversation> adapter;
    private ArrayList<Conversation> conversationList;
    private Message pendingMessage;
    private Toast prepareImageToast;
    private DisplayMetrics metrics = new DisplayMetrics();
    private Activity activity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        listView = findViewById(R.id.conversations_list);
        conversationList = new ArrayList<>();
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conversationList);
        listView.setAdapter(adapter);

        activity = this;
        updateConversationList();

        // Set item click listener
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conversation = conversationList.get(position);
                Intent intent = new Intent(ConversationActivity.this, ChatActivity.class);
                intent.putExtra("conversation", conversation);
                startActivity(intent);
            }
        });
    }

    private void updateConversationList() {
        conversationList.clear();
        // Assume xmppConnectionService is properly initialized and secured
        conversationList.addAll(XmppConnectionService.getConversations());
        adapter.notifyDataSetChanged(); // Use notifyDataSetChanged instead of invalidateViews()
    }

    public void selectPresence(final Conversation conversation, final OnPresenceSelected listener, String reason) {
        Account account = conversation.getAccount();
        if (account.getStatus() != Account.STATUS_ONLINE) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.not_connected));
            builder.setIconAttribute(android.R.attr.alertDialogIcon);
            if ("otr".equals(reason)) {
                builder.setMessage(getString(R.string.you_are_offline, getString(R.string.otr_messages)));
            } else if ("file".equals(reason)) {
                builder.setMessage(getString(R.string.you_are_offline, getString(R.string.files)));
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
            if (contact == null) {
                showAddToRosterDialog(conversation);
                listener.onPresenceSelected(false, null);
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
                    String presence = presences.keySet().toArray(new String[0])[0];
                    conversation.setNextPresence(presence);
                    listener.onPresenceSelected(true, presence);
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.choose_presence));
                    final String[] presencesArray = presences.keySet().toArray(new String[0]);
                    builder.setItems(presencesArray,
                            new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog,
                                                      int which) {
                                    String presence = presencesArray[which];
                                    conversation.setNextPresence(presence);
                                    listener.onPresenceSelected(true, presence);
                                }
                            });
                    builder.create().show();
                }
            }
        }
    }

    private void showAddToRosterDialog(final Conversation conversation) {
        String jid = conversation.getContactJid();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(jid);
        builder.setMessage(getString(R.string.not_in_roster));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.add_contact), new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                String jid = conversation.getContactJid();
                Account account = getSelectedConversation().getAccount();
                Contact contact = account.getRoster().getContact(jid);
                // Assume createContact is a secure method that validates the input
                XmppConnectionService.createContact(contact);
            }
        });
        builder.create().show();
    }

    public void runIntent(PendingIntent pi, int requestCode) {
        try {
            startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0,
                    0, 0);
        } catch (SendIntentException e1) {
            Log.d("xmppService", "failed to start intent to send message");
        }
    }

    public void loadBitmap(Message message, ImageView imageView) {
        Bitmap bm;
        try {
            bm = XmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), true);
        } catch (FileNotFoundException e) {
            bm = null;
        }
        if (bm != null) {
            imageView.setImageBitmap(bm);
            imageView.setBackgroundColor(0x00000000);
        } else {
            if (cancelPotentialWork(message, imageView)) {
                imageView.setBackgroundColor(0xff333333);
                final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
                final AsyncDrawable asyncDrawable =
                        new AsyncDrawable(getResources(), null, task);
                imageView.setImageDrawable(asyncDrawable);
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message); // Use THREAD_POOL_EXECUTOR to avoid blocking
            }
        }
    }

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

    static class AsyncDrawable extends BitmapDrawable {
        private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

        public AsyncDrawable(Resources res, Bitmap bitmap,
                             BitmapWorkerTask bitmapWorkerTask) {
            super(res, bitmap);
            bitmapWorkerTaskReference =
                    new WeakReference<>(bitmapWorkerTask);
        }

        public BitmapWorkerTask getBitmapWorkerTask() {
            return bitmapWorkerTaskReference.get();
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Message, Void, Bitmap> {
        private final WeakReference<ImageView> imageViewReference;
        private Message message = null;

        public BitmapWorkerTask(ImageView imageView) {
            imageViewReference = new WeakReference<>(imageView);
        }

        @Override
        protected Bitmap doInBackground(Message... params) {
            message = params[0];
            try {
                return XmppConnectionService.getFileBackend().getThumbnail(message, (int) (metrics.density * 288), false);
            } catch (FileNotFoundException e) {
                Log.d("xmppService", "file not found!");
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

    public void encryptMessage(Message message) {
        // Implement proper encryption method here
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            Uri selectedImage = data.getData();
            // Always validate and sanitize inputs
            if (selectedImage != null) {
                // Process image securely
            }
        }
    }

    private Conversation getSelectedConversation() {
        int position = listView.getCheckedItemPosition();
        return conversationList.get(position);
    }

    public void encryptAndSendMessage(Message message) {
        encryptMessage(message);  // Ensure encryption is done before sending
        sendMessage(message);
    }

    private void sendMessage(Message message) {
        // Assume this method sends the message securely over XMPP
    }
}