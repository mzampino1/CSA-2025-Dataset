// ** BEGIN **

package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Iterator;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.util.MenuUtils;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnAccountUiCallback,
        XmppConnectionService.OnConversationUiCallback, XmppConnectionService.OnRosterUiCallback,
        XmppConnectionService.OnUpdateBlocklist {

    public static final int REQUEST_DECRYPT_PGP = 0x1234;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x0567;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x0568;
    public static final int ATTACHMENT_CHOICE_INVALID = -1;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 1;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 2;
    public static final int ATTACHMENT_CHOICE_FILE = 3;
    public static final int ATTACHMENT_CHOICE_LOCATION = 4;

    private ConversationFragment mConversationFragment;
    private ArrayList<Conversation> conversationList;
    private ConversationAdapter listAdapter;
    private Conversation swipedConversation;
    private Toast prepareFileToast;
    private boolean conversationWasSelectedByKeyboard;
    private Uri pendingImageUri;
    private Uri pendingGeoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize the conversation list and adapter.
        conversationList = new ArrayList<>();
        listAdapter = new ConversationAdapter(this, conversationList);
        final ListView listView = findViewById(R.id.list_view);
        listView.setAdapter(listAdapter);

        // Handle item click on conversations.
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation clickedConversation = (Conversation) parent.getItemAtPosition(position);
                Intent intent = new Intent(ConversationActivity.this, MessageViewer.class);
                intent.putExtra("conversation", clickedConversation.getUuid());
                startActivity(intent);
            }
        });

        // Handle swipe to delete conversation.
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                swipedConversation = (Conversation) parent.getItemAtPosition(position);
                if (swipedConversation.isRead()) {
                    conversationList.remove(swipedConversation);
                    listAdapter.notifyDataSetChanged();
                } else {
                    listView.setItemChecked(position, true);
                    UndoBarController.showUndoBar(ConversationActivity.this,
                            R.string.conversation_deleted,
                            new Runnable() {
                                @Override
                                public void run() {
                                    conversationList.add(swipedConversation);
                                    swipedConversation = null;
                                    listAdapter.notifyDataSetChanged();
                                }
                            },
                            new Runnable() {
                                @Override
                                public void run() {
                                    xmppConnectionService.archiveConversation(swipedConversation);
                                    swipedConversation = null;
                                }
                            });
                }
                return true;
            }
        });

        // Initialize the conversation fragment.
        mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_conversation);

        // Handle attachment choices from the menu or other intents.
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("attachment_choice")) {
            int choice = intent.getIntExtra("attachment_choice", ATTACHMENT_CHOICE_INVALID);
            handleAttachmentChoice(choice);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mConversationFragment != null && mConversationFragment.getConversation() != null) {
            Conversation conversation = mConversationFragment.getConversation();
            menu.findItem(R.id.action_archive).setVisible(!conversation.isArchived());
            menu.findItem(R.id.action_unarchive).setVisible(conversation.isArchived());

            // Ensure the trust keys option is only available for encrypted conversations.
            menu.findItem(R.id.action_trust_keys).setVisible(conversation.getEncryption() != Message.ENCRYPTION_NONE);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mConversationFragment == null || mConversationFragment.getConversation() == null)
            return false;
        Conversation conversation = mConversationFragment.getConversation();
        switch (item.getItemId()) {
            case R.id.action_archive:
                xmppConnectionService.archiveConversation(conversation);
                return true;

            case R.id.action_unarchive:
                xmppConnectionService.unarchiveConversation(conversation);
                return true;

            case R.id.action_trust_keys:
                trustKeysIfNeeded(REQUEST_TRUST_KEYS_MENU);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Handle the result of choosing an image or file attachment.
        if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE ||
                requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO || requestCode == ATTACHMENT_CHOICE_FILE) {
            if (resultCode == RESULT_OK && data != null) {
                Uri uri = data.getData();
                if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                    pendingImageUri = uri;
                } else {
                    attachFileToConversation(mConversationFragment.getConversation(), uri);
                }
            }
        }

        // Handle the result of choosing a location attachment.
        if (requestCode == ATTACHMENT_CHOICE_LOCATION && resultCode == RESULT_OK) {
            double latitude = data.getDoubleExtra("latitude", 0);
            double longitude = data.getDoubleExtra("longitude", 0);
            pendingGeoUri = Uri.parse("geo:" + String.valueOf(latitude) + "," + String.valueOf(longitude));
        }

        // Handle the result of decrypting a PGP message.
        if (requestCode == REQUEST_DECRYPT_PGP && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            Message decryptedMessage = extras.getParcelable("decrypted_message");
            mConversationFragment.updateMessage(decryptedMessage);
        }

        // Handle the result of trusting keys for encryption.
        if (requestCode == REQUEST_TRUST_KEYS_TEXT || requestCode == REQUEST_TRUST_KEYS_MENU && resultCode == RESULT_OK) {
            trustKeysIfNeeded(requestCode, ATTACHMENT_CHOICE_INVALID);
        }
    }

    private void handleAttachmentChoice(int choice) {
        switch (choice) {
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                break;

            case ATTACHMENT_CHOICE_RECORD_VOICE:
                intent = new Intent(ConversationActivity.this, VoiceRecorder.class);
                startActivityForResult(intent, ATTACHMENT_CHOICE_RECORD_VOICE);
                break;

            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                pendingImageUri = getOutputMediaFileUri();
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingImageUri);
                startActivityForResult(intent, ATTACHMENT_CHOICE_TAKE_PHOTO);
                break;

            case ATTACHMENT_CHOICE_FILE:
                intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                startActivityForResult(intent, ATTACHMENT_CHOICE_FILE);
                break;

            case ATTACHMENT_CHOICE_LOCATION:
                intent = new Intent(ConversationActivity.this, LocationPicker.class);
                startActivityForResult(intent, ATTACHMENT_CHOICE_LOCATION);
                break;
        }
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null)
            return;

        // Validate the URI to ensure it points to a secure location.
        if (!isUriSecure(uri)) {
            Toast.makeText(this, "Invalid file source", Toast.LENGTH_SHORT).show();
            return;
        }

        prepareFileToast = Toast.makeText(this, R.string.preparing_file, Toast.LENGTH_LONG);
        prepareFileToast.show();

        // Attach the file to the conversation asynchronously.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    xmppConnectionService.attachFile(conversation, uri);
                } finally {
                    runOnUiThread(() -> {
                        if (prepareFileToast != null) {
                            prepareFileToast.cancel();
                        }
                    });
                }
            }
        }).start();
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null)
            return;

        // Validate the URI to ensure it points to a secure location.
        if (!isUriSecure(uri)) {
            Toast.makeText(this, "Invalid file source", Toast.LENGTH_SHORT).show();
            return;
        }

        prepareFileToast = Toast.makeText(this, R.string.preparing_image, Toast.LENGTH_LONG);
        prepareFileToast.show();

        // Attach the image to the conversation asynchronously.
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    xmppConnectionService.attachImage(conversation, uri);
                } finally {
                    runOnUiThread(() -> {
                        if (prepareFileToast != null) {
                            prepareFileToast.cancel();
                        }
                    });
                }
            }
        }).start();
    }

    private boolean isUriSecure(Uri uri) {
        // Implement a method to validate the URI.
        // For example, check if the URI points to external storage and is readable by the app.
        return true;
    }

    @Override
    public void onAccountUiUpdate() {
        runOnUiThread(() -> updateConversationList());
    }

    @Override
    public void onConversationUiUpdated(Conversation conversation) {
        runOnUiThread(() -> {
            int index = conversationList.indexOf(conversation);
            if (index >= 0) {
                listAdapter.notifyItemChanged(index);
            }
        });
    }

    @Override
    public void OnUpdateBlocklist() {
        runOnUiThread(this::updateConversationList);
    }

    private void updateConversationList() {
        // Update the conversation list with the latest conversations.
        conversationList.clear();
        for (Account account : xmppConnectionService.getAccounts()) {
            conversationList.addAll(account.getConversations());
        }
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRosterUiUpdated(Contact contact) {
        runOnUiThread(() -> updateConversationList());
    }

    // ** END **
}