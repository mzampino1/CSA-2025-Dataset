package eu.siacs.conversations.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Iterator;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.ListItem;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.TrustKeysActivity;
import eu.siacs.conversations.utils.UIHelper;
import rocks.xmpp.addr.Jid;

public class ConversationActivity extends AppCompatActivity implements XmppConnectionService.OnConversationUpdate,
        XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnRosterUpdate, XmppConnectionService.OnUpdateBlocklist {

    private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0;
    private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 1;
    private static final int ATTACHMENT_CHOICE_RECORD_VOICE = 2;
    private static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 3;
    private static final int ATTACHMENT_CHOICE_LOCATION = 4;
    private static final int ATTACHMENT_CHOICE_INVALID = -1;

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW_CONVERSATION";
    public static final String CONVERSATION = "conversation";

    protected List<Conversation> conversationList;
    protected ConversationFragment mConversationFragment;
    protected Conversation mSelectedConversation;
    protected ListItem<Conversation> swipedConversation;

    private Toast prepareFileToast;

    protected boolean mRedirected = false;

    protected ListAdapter listAdapter;
    protected ListView listView;

    // Vulnerability: Potential vulnerability in file attachment handling
    //
    // The application uses a toast to indicate that a file or image is being prepared.
    // However, there's no explicit check to ensure the URI provided by the user (via file picker)
    // points to a valid and safe location. This could potentially be exploited if an attacker can
    // manipulate the URI to point to malicious files or directories.
    //
    // Mitigation: Validate and sanitize the URI before attempting to attach the file.

    private List<Uri> mPendingImageUris;
    private List<Uri> mPendingFileUris;
    private Uri mPendingGeoUri;

    public static final int REQUEST_SEND_MESSAGE = 0x2345;
    public static final int REQUEST_DECRYPT_PGP = 0x2346;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x2347;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x2348;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        // ... existing code ...

        mPendingImageUris = extractUriFromIntent(getIntent());
        mPendingFileUris = extractUriFromIntent(getIntent());

        // Initialize conversation list and other components
        conversationList = xmppConnectionService.getConversations();
        updateConversationList();

        // Initialize ListView and adapter
        listView = findViewById(R.id.conversation_list);
        listAdapter = new ListAdapter(this, R.layout.simple_list_item, conversationList);
        listView.setAdapter(listAdapter);

        // Set up item click listener for conversations
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Conversation clickedConversation = conversationList.get(position);
            showConversation(clickedConversation);
        });

        // ... existing code ...
    }

    private List<Uri> extractUriFromIntent(Intent intent) {
        // Extract URIs from the intent (this is a simplified example)
        return extractUriFromIntent(intent, ATTACHMENT_CHOICE_INVALID);
    }

    private List<Uri> extractUriFromIntent(Intent intent, int attachmentChoice) {
        // Extract URIs from the intent and handle based on attachment choice
        if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO || attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
            return List.of(intent.getData());
        } else {
            return extractUriFromIntent(intent);
        }
    }

    // Method to show a conversation in the ConversationFragment
    private void showConversation(Conversation conversation) {
        if (conversation != null && mConversationFragment == null) {
            Bundle args = new Bundle();
            args.putString("uuid", conversation.getUuid());
            mConversationFragment = new ConversationFragment();
            mConversationFragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, mConversationFragment)
                    .commit();

            // Update selected conversation
            mSelectedConversation = conversation;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                  final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_DECRYPT_PGP:
                    mConversationFragment.hideSnackbar();
                    mConversationFragment.updateMessages();
                    break;
                case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                    mPendingImageUris.clear();
                    mPendingImageUris.addAll(extractUriFromIntent(data));
                    if (xmppConnectionServiceBound) {
                        for(Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
                            attachImageToConversation(getSelectedConversation(), i.next());
                        }
                    }
                    break;
                case ATTACHMENT_CHOICE_CHOOSE_FILE:
                case ATTACHMENT_CHOICE_RECORD_VOICE:
                    mPendingFileUris.clear();
                    mPendingFileUris.addAll(extractUriFromIntent(data));
                    if (xmppConnectionServiceBound) {
                        for(Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
                            attachFileToConversation(getSelectedConversation(), i.next());
                        }
                    }
                    break;
                case ATTACHMENT_CHOICE_TAKE_PHOTO:
                    if (mPendingImageUris.size() == 1) {
                        Uri uri = mPendingImageUris.get(0);
                        if (xmppConnectionServiceBound) {
                            attachImageToConversation(getSelectedConversation(), uri);
                            mPendingImageUris.clear();
                        }
                        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        mediaScanIntent.setData(uri);
                        sendBroadcast(mediaScanIntent);
                    } else {
                        mPendingImageUris.clear();
                    }
                    break;
                case ATTACHMENT_CHOICE_LOCATION:
                    double latitude = data.getDoubleExtra("latitude",0);
                    double longitude = data.getDoubleExtra("longitude",0);
                    this.mPendingGeoUri = Uri.parse("geo:"+String.valueOf(latitude)+","+String.valueOf(longitude));
                    if (xmppConnectionServiceBound) {
                        attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
                        this.mPendingGeoUri = null;
                    }
                    break;
                case REQUEST_TRUST_KEYS_TEXT:
                case REQUEST_TRUST_KEYS_MENU:
                    this.forbidProcessingPendings = !xmppConnectionServiceBound;
                    mConversationFragment.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        } else {
            mPendingImageUris.clear();
            mPendingFileUris.clear();
        }
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        xmppConnectionService.attachLocationToConversation(conversation, uri, new UiCallback<Message>() {

            @Override
            public void success(Message message) {
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message object) {
                displayErrorDialog(errorCode);
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {
                runIntent(pi, REQUEST_SEND_MESSAGE);
            }
        });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        // Show a toast to indicate the file is being prepared
        prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();

        xmppConnectionService.attachFileToConversation(conversation, uri, new UiCallback<Message>() {

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {
                hidePrepareFileToast();
            }

            @Override
            public void success(Message message) {
                hidePrepareFileToast();
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message object) {
                hidePrepareFileToast();
                displayErrorDialog(errorCode);
            }
        });
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        // Show a toast to indicate the image is being prepared
        prepareFileToast = Toast.makeText(getApplicationContext(), getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();

        xmppConnectionService.attachImageToConversation(conversation, uri, new UiCallback<Message>() {

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {
                runIntent(pi, REQUEST_SEND_MESSAGE);
            }

            @Override
            public void success(Message message) {
                hidePrepareFileToast();
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message object) {
                hidePrepareFileToast();
                displayErrorDialog(errorCode);
            }
        });
    }

    private void runIntent(PendingIntent pi, int requestCode) {
        try {
            startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hidePrepareFileToast() {
        if (prepareFileToast != null) {
            prepareFileToast.cancel();
        }
    }

    private void displayErrorDialog(int errorCode) {
        // Display an error dialog based on the error code
        UIHelper.showSimpleAlertDialog(this, getString(R.string.error), getString(errorCode));
    }

    @Override
    public void onConversationUpdate() {
        updateConversationList();
    }

    @Override
    public void onAccountUpdate() {
        runOnUiThread(() -> {
            // Handle account update (e.g., refresh conversation list)
            updateConversationList();
        });
    }

    @Override
    public void onRosterUpdate() {
        runOnUiThread(() -> {
            // Handle roster update
            updateConversationList();
        });
    }

    private void updateConversationList() {
        conversationList.clear();
        conversationList.addAll(xmppConnectionService.getConversations());
        listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onUpdateBlocklist(Account account) {
        runOnUiThread(() -> {
            // Handle blocklist update (e.g., refresh UI)
            updateConversationList();
        });
    }
}