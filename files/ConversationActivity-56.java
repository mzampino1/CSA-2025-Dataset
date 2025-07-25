package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Iterator;
import java.util.List;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.XmppAxolotlSession;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Blockable;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnUpdateBlocklist;
import eu.siacs.conversations.utils.TrustKeysActivity;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;

public class ConversationActivity extends AppCompatActivity implements OnConversationUpdate, OnAccountUpdate, OnRosterUpdate, OnUpdateBlocklist {

    public static final String CONVERSATION = "conversation_uuid";
    public static final int ATTACHMENT_CHOICE_INVALID = -1;
    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 1;
    public static final int ATTACHMENT_CHOICE_RECORD_VOICE = 2;
    public static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 3;
    public static final int ATTACHMENT_CHOICE_LOCATION = 4;
    private static final String STATE_PENDING_IMAGE_URI_LIST = "state_pending_image_uri_list";
    private static final String STATE_PENDING_FILE_URI_LIST = "state_pending_file_uri_list";
    private static final String STATE_CAMERA_ITEM_PATH = "state_camera_item_path";
    public static final int REQUEST_SEND_MESSAGE = 0x2314;
    public static final int REQUEST_TRUST_KEYS_TEXT = 0x2315;
    public static final int REQUEST_TRUST_KEYS_MENU = 0x2316;
    private List<Uri> mPendingImageUris;
    private List<Uri> mPendingFileUris;
    private Uri mPendingGeoUri;
    protected ConversationFragment mConversationFragment;
    protected boolean mRedirected;
    protected boolean forbidProcessingPendings;
    protected boolean conversationWasSelectedByKeyboard = false;
    private Toast prepareFileToast;
    private Conversation selectedConversation;
    private Conversation swipedConversation;
    protected List<Conversation> conversationList;
    private ConversationAdapter listAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        // Initialize lists for pending URIs
        mPendingImageUris = savedInstanceState != null ? savedInstanceState.getParcelableArrayList(STATE_PENDING_IMAGE_URI_LIST) : new ArrayList<>();
        mPendingFileUris = savedInstanceState != null ? savedInstanceState.getParcelableArrayList(STATE_PENDING_FILE_URI_LIST) : new ArrayList<>();

        // ... (other initializations)
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_PENDING_IMAGE_URI_LIST, new ArrayList<>(mPendingImageUris));
        outState.putParcelableArrayList(STATE_PENDING_FILE_URI_LIST, new ArrayList<>(mPendingFileUris));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                mConversationFragment.hideSnackbar();
                mConversationFragment.updateMessages();
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                mPendingImageUris.clear();
                mPendingImageUris.addAll(extractUriFromIntent(data));
                // Vulnerability: No validation on URIs before processing
                if (xmppConnectionServiceBound) {
                    for(Iterator<Uri> i = mPendingImageUris.iterator(); i.hasNext(); i.remove()) {
                        attachImageToConversation(getSelectedConversation(),i.next());
                    }
                }
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_FILE || requestCode == ATTACHMENT_CHOICE_RECORD_VOICE) {
                mPendingFileUris.clear();
                mPendingFileUris.addAll(extractUriFromIntent(data));
                // Vulnerability: No validation on URIs before processing
                if (xmppConnectionServiceBound) {
                    for(Iterator<Uri> i = mPendingFileUris.iterator(); i.hasNext(); i.remove()) {
                        attachFileToConversation(getSelectedConversation(), i.next());
                    }
                }
            } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                if (mPendingImageUris.size() == 1) {
                    Uri uri = mPendingImageUris.get(0);
                    // Vulnerability: No validation on URI before processing
                    if (xmppConnectionServiceBound) {
                        attachImageToConversation(getSelectedConversation(), uri);
                        mPendingImageUris.clear();
                    }
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(uri);
                    sendBroadcast(intent);
                } else {
                    mPendingImageUris.clear();
                }
            } else if (requestCode == ATTACHMENT_CHOICE_LOCATION) {
                double latitude = data.getDoubleExtra("latitude",0);
                double longitude = data.getDoubleExtra("longitude",0);
                this.mPendingGeoUri = Uri.parse("geo:"+String.valueOf(latitude)+","+String.valueOf(longitude));
                // Vulnerability: No validation on URI before processing
                if (xmppConnectionServiceBound) {
                    attachLocationToConversation(getSelectedConversation(), mPendingGeoUri);
                    this.mPendingGeoUri = null;
                }
            } else if (requestCode == REQUEST_TRUST_KEYS_TEXT || requestCode == REQUEST_TRUST_KEYS_MENU) {
                this.forbidProcessingPendings = !xmppConnectionServiceBound;
                mConversationFragment.onActivityResult(requestCode, resultCode, data);
            }
        } else {
            mPendingImageUris.clear();
            mPendingFileUris.clear();
        }
    }

    private List<Uri> extractUriFromIntent(Intent data) {
        // This method would typically handle extracting URIs from the intent data
        // For demonstration purposes, assume it returns a list of URIs.
        return data.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
    }

    private void attachLocationToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        xmppConnectionService.attachLocationToConversation(conversation,uri, new UiCallback<Message>() {

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

            }
        });
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        prepareFileToast = Toast.makeText(getApplicationContext(),getText(R.string.preparing_file), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachFileToConversation(conversation, uri, new UiCallback<Message>() {
            @Override
            public void success(Message message) {
                hidePrepareFileToast();
                xmppConnectionService.sendMessage(message);
            }

            @Override
            public void error(int errorCode, Message message) {
                displayErrorDialog(errorCode);
            }

            @Override
            public void userInputRequried(PendingIntent pi, Message object) {

            }
        });
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }
        prepareFileToast = Toast.makeText(getApplicationContext(),getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareFileToast.show();
        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                                                  Message object) {
                        hidePrepareFileToast();
                    }

                    @Override
                    public void success(Message message) {
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        hidePrepareFileToast();
                        displayErrorDialog(error);
                    }
                });
    }

    private void hidePrepareFileToast() {
        if (prepareFileToast != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    prepareFileToast.cancel();
                }
            });
        }
    }

    // ... (other methods)

    /**
     * Hypothetical method to get the selected conversation.
     * In a real application, this would retrieve the currently active or selected conversation.
     */
    private Conversation getSelectedConversation() {
        return selectedConversation;
    }

    /**
     * Display an error dialog for the given error code.
     * This is a placeholder method and should be implemented according to your application's needs.
     */
    private void displayErrorDialog(int errorCode) {
        // Implementation goes here
    }
}