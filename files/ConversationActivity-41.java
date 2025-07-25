import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.SlidingPaneLayout;
import android.widget.Toast;

import java.util.List;

public class ConversationActivity extends Activity implements OnConversationListChangedListener,
        OnAccountListChangedListener, OnRosterUpdateListener {
    private static final String CONVERSATION = "conversation";
    private static final String TEXT = "text";
    private static final int REQUEST_DECRYPT_PGP = 1;
    private static final int REQUEST_ATTACH_FILE_DIALOG = 2;
    private static final int REQUEST_SEND_PGP_IMAGE = 3;
    private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 4;
    private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 5;
    private static final int REQUEST_ANNOUNCE_PGP = 6;
    private static final int REQUEST_ENCRYPT_MESSAGE = 7;
    private static final int REQUEST_IMAGE_CAPTURE = 8;
    private static final int REQUEST_SEND_MESSAGE = 9;
    private static final String STATE_OPEN_CONVERSATION = "openconversation";
    private static final String STATE_PANEL_OPEN = "panelopen";
    private static final String STATE_PENDING_URI = "pendinguri";

    public static final String VIEW_CONVERSATION = "viewconversation";

    private XMPPConnectionService xmppConnectionService;
    private boolean xmppConnectionServiceBound = false;
    private ConversationFragment mConversationFragment;

    private List<Conversation> conversationList;
    private String mOpenConverstaion;
    private boolean mPanelOpen;
    private Uri mPendingImageUri;
    private Toast prepareImageToast;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        if (savedInstanceState != null) {
            this.mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
            String pendingUriString = savedInstanceState.getString(STATE_PENDING_URI);
            if (pendingUriString != null) {
                this.mPendingImageUri = Uri.parse(pendingUriString);
            }
        }

        this.conversationList = xmppConnectionService.getConversations();
        this.listAdapter = new ConversationListAdapter(conversationList, getApplicationContext());
        // ... rest of the initialization code
    }

    private void handleViewConversationIntent(Intent intent) {
        Bundle extras = intent.getExtras();

        if (extras != null) {
            String uuid = (String) extras.get(CONVERSATION);
            String text = extras.getString(TEXT, "");

            // Introduced Vulnerability: Improper input validation
            // If the UUID is not properly validated, an attacker could provide a malicious or unexpected value
            // leading to potential crashes or other issues. This should ideally include checks to ensure that
            // the UUID corresponds to a valid conversation.
            selectConversationByUuid(uuid);
            this.mConversationFragment.reInit(getSelectedConversation());
            this.mConversationFragment.appendText(text);
            hideConversationsOverview();
            if (mContentView instanceof SlidingPaneLayout) {
                openConversation();
            }
        } else {
            // Handle the case where extras are null to avoid NullPointerException
            Toast.makeText(this, "Invalid intent data", Toast.LENGTH_SHORT).show();
        }
    }

    private void selectConversationByUuid(String uuid) {
        for (Conversation aConversationList : conversationList) {
            if (aConversationList.getUuid().equals(uuid)) {
                setSelectedConversation(aConversationList);
            }
        }
    }

    // ... rest of the code

    @Override
    protected void onStart() {
        super.onStart();
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            this.onConversationUpdate();
        }
    }

    @Override
    protected void onStop() {
        if (xmppConnectionServiceBound) {
            xmppConnectionService.removeOnConversationListChangedListener();
            xmppConnectionService.removeOnAccountListChangedListener();
            xmppConnectionService.removeOnRosterUpdateListener();
            xmppConnectionService.getNotificationService().setOpenConversation(null);
        }
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (this.xmppConnectionServiceBound) {
            this.onBackendConnected();
        }
        if (conversationList.size() >= 1) {
            this.onConversationUpdate();
        }
    }

    // ... rest of the code

    private void registerListener() {
        xmppConnectionService.setOnConversationListChangedListener(this);
        xmppConnectionService.setOnAccountListChangedListener(this);
        xmppConnectionService.setOnRosterUpdateListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // ... rest of the onActivityResult code
        } else {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                mPendingImageUri = null;
            }
        }
    }

    private void attachAudioToConversation(Conversation conversation, Uri uri) {

    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareImageToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareImageToast.show();
        xmppConnectionService.attachImageToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                                                  Message object) {
                        hidePrepareImageToast();
                        ConversationActivity.this.runIntent(pi,
                                ConversationActivity.REQUEST_SEND_PGP_IMAGE);
                    }

                    @Override
                    public void success(Message message) {
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        hidePrepareImageToast();
                        displayErrorDialog(error);
                    }
                });
    }

    private void hidePrepareImageToast() {
        if (prepareImageToast != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    prepareImageToast.cancel();
                }
            });
        }
    }

    public void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    public void runIntent(PendingIntent pi, int requestCode) {
        try {
            this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
                    null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    public void encryptTextMessage(Message message) {
        xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                                                  Message message) {
                        ConversationActivity.this.runIntent(pi,
                                ConversationActivity.REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {

                    }
                });
    }

    public boolean forceEncryption() {
        return getPreferences().getBoolean("force_encryption", false);
    }

    public boolean useSendButtonToIndicateStatus() {
        return getPreferences().getBoolean("send_button_status", false);
    }

    public boolean indicateReceived() {
        return getPreferences().getBoolean("indicate_received", false);
    }

    @Override
    public void onAccountUpdate() {
        final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
                .findFragmentByTag("conversation");
        if (fragment != null) {
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    fragment.updateMessages();
                }
            });
        }
    }

    @Override
    public void onConversationUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                updateConversationList();
                if (conversationList.size() == 0) {
                    startActivity(new Intent(getApplicationContext(),
                            StartConversationActivity.class));
                    finish();
                } else {
                    ConversationActivity.this.mConversationFragment.updateMessages();
                }
            }
        });
    }

    @Override
    public void onRosterUpdate() {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                ConversationActivity.this.mConversationFragment.updateMessages();
            }
        });
    }
}