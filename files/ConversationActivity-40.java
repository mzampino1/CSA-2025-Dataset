package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends FragmentActivity implements XmppConnectionService.OnConversationListChangedListener, XmppConnectionService.OnAccountListChangedListener, XmppConnectionService.OnRosterUpdateListener {

    private static final String STATE_OPEN_CONVERSATION = "openconversation";
    private static final String STATE_PANEL_OPEN = "panelpanel";
    private static final String STATE_PENDING_URI = "pending_uri";

    public static final String CONVERSATION = "conversation";
    public static final String TEXT = "text";
    public static final String VIEW_CONVERSATION = "conversation/view";

    private static final int REQUEST_IMAGE_CAPTURE = 0x1234;
    private static final int REQUEST_SEND_MESSAGE = 0x1235;
    private static final int REQUEST_SEND_PGP_IMAGE = 0x1236;
    private static final int REQUEST_ENCRYPT_MESSAGE = 0x1237;
    private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x1238;
    private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x1239;

    public static final int REQUEST_DECRYPT_PGP = 0x1240;
    public static final int REQUEST_ANNOUNCE_PGP = 0x1241;

    private SlidingPaneLayout mContentView;
    private ConversationFragment mConversationFragment;
    private ArrayList<Conversation> conversationList = new ArrayList<>();
    private ConversationsOverview conversationsOverview;
    private String mOpenConverstaion = null;
    private boolean mPanelOpen = false;

    private Uri mPendingImageUri = null;
    private Toast prepareImageToast;
    private XmppConnectionService xmppConnectionService;
    private boolean xmppConnectionServiceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        mContentView = findViewById(R.id.conversations_overview_pane_layout);
        if (mContentView != null) {
            mContentView.setPanelSlideListener(new SlidingPaneLayout.SimplePanelSlideListener() {

                @Override
                public void onPanelClosed(View view) {
                    ConversationActivity.this.mPanelOpen = false;
                }

                @Override
                public void onPanelOpened(View view) {
                    ConversationActivity.this.mPanelOpen = true;
                }
            });
        }

        mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.conversation_fragment);

        if (savedInstanceState != null) {
            mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
            final String uriString = savedInstanceState.getString(STATE_PENDING_URI);
            if (uriString != null) {
                this.mPendingImageUri = Uri.parse(uriString);
            }
        }

        conversationsOverview = new ConversationsOverview(this, conversationList);

        if (!isConversationsOverviewHideable()) {
            openConversation();
        }

        // ... rest of the code remains the same
    }

    private void showConversationsOverview() {
        mContentView.openPane(conversationsOverview);
    }

    private void hideConversationsOverview() {
        mContentView.closePane(conversationsOverview);
    }

    public boolean isConversationsOverviewHideable() {
        return (mContentView != null) && mContentView.isSlideable();
    }

    private void openConversation() {
        if (isConversationsOverviewHideable()) {
            mContentView.openPane(mConversationFragment.getView());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
        if (this.mPendingImageUri != null) {
            savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    private boolean isConversationsOverviewVisable() {
        return mContentView == null || !mContentView.isSlideable() || mContentView.isOpen();
    }

    @Override
    void onBackendConnected() {
        this.registerListener();
        updateConversationList();

        if (xmppConnectionService.getAccounts().size() == 0) {
            startActivity(new Intent(this, EditAccountActivity.class));
        } else if (conversationList.size() <= 0) {
            startActivity(new Intent(this, StartConversationActivity.class));
            finish();
        } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
            handleViewConversationIntent(getIntent());
        } else if (mOpenConverstaion != null) {
            selectConversationByUuid(mOpenConverstaion);
            if (mPanelOpen) {
                showConversationsOverview();
            } else {
                if (isConversationsOverviewHideable()) {
                    openConversation();
                }
            }
            this.mConversationFragment.reInit(getSelectedConversation());
            mOpenConverstaion = null;
        } else if (getSelectedConversation() == null) {
            showConversationsOverview();
            mPendingImageUri = null;
            setSelectedConversation(conversationList.get(0));
            this.mConversationFragment.reInit(getSelectedConversation());
        }

        if (mPendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), mPendingImageUri);
            mPendingImageUri = null;
        }
        ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
        setIntent(new Intent());
    }

    private void handleViewConversationIntent(Intent intent) {
        String uuid = (String) intent.getExtras().get(CONVERSATION);
        String text = intent.getExtras().getString(TEXT, "");
        selectConversationByUuid(uuid);
        this.mConversationFragment.reInit(getSelectedConversation());
        this.mConversationFragment.appendText(text);
        hideConversationsOverview();
        if (mContentView instanceof SlidingPaneLayout) {
            openConversation();
        }
    }

    private void selectConversationByUuid(String uuid) {
        for (int i = 0; i < conversationList.size(); ++i) {
            if (conversationList.get(i).getUuid().equals(uuid)) {
                setSelectedConversation(conversationList.get(i));
            }
        }
    }

    public void registerListener() {
        xmppConnectionService.setOnConversationListChangedListener(this);
        xmppConnectionService.setOnAccountListChangedListener(this);
        xmppConnectionService.setOnRosterUpdateListener(this);
    }

    private void showConversationsOverview(boolean open) {
        if (open) {
            showConversationsOverview();
        } else {
            hideConversationsOverview();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                 final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hideSnackbar();
                    selectedFragment.updateMessages();
                }
            } else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
                mPendingImageUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(),
                            mPendingImageUri);
                    mPendingImageUri = null;
                }
            } else if (requestCode == REQUEST_SEND_PGP_IMAGE) {

            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
            } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
            } else if (requestCode == REQUEST_ANNOUNCE_PGP) {
                announcePgp(getSelectedConversation().getAccount(),
                        getSelectedConversation());
            } else if (requestCode == REQUEST_ENCRYPT_MESSAGE) {
                // encryptTextMessage();
            } else if (requestCode == REQUEST_IMAGE_CAPTURE && mPendingImageUri != null) {
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(),
                            mPendingImageUri);
                    mPendingImageUri = null;
                }
                Intent intent = new Intent(
                        Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(mPendingImageUri);
                sendBroadcast(intent);
            } else if (requestCode == REQUEST_RECORD_AUDIO) {
                attachAudioToConversation(getSelectedConversation(),
                        data.getData());
            }
        } else {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                mPendingImageUri = null;
            }
        }
    }

    private void attachFile(int type) {
        Intent intent = new Intent();
        if (type == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
        } else if (type == ATTACHMENT_CHOICE_TAKE_PHOTO) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            if (photoFile != null) {
                mPendingImageUri = Uri.fromFile(photoFile);
                intent.setAction(MediaStore.ACTION_IMAGE_CAPTURE);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, mPendingImageUri);
            }
        }

        startActivityForResult(Intent.createChooser(intent,
                getResources().getText(R.string.attach_file)), type);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = "";
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(null);

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    private void attachAudioToConversation(Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            Message message = new Message(conversation, "", Message.STATUS_OFFERED, 0);
            message.setTransferable(uri);
            xmppConnectionService.sendMessage(message);
        }
    }

    private void attachImageToConversation(Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            Message message = new Message(conversation, "", Message.STATUS_OFFERED, 0);
            message.setTransferable(uri);
            xmppConnectionService.sendMessage(message);
        }
    }

    private void displayMessage(Message message) {
        if (message.getConversation() == getSelectedConversation()) {
            mConversationFragment.displayMessage(message);
        }
    }

    public Conversation getSelectedConversation() {
        return conversationList.isEmpty() ? null : conversationList.get(0); // Assuming the first one is selected for simplicity
    }

    private void setSelectedConversation(Conversation conversation) {
        if (conversation != null && !conversation.equals(getSelectedConversation())) {
            int pos = conversationList.indexOf(conversation);
            if (pos >= 0) {
                conversationsOverview.setSelection(pos);
            }
        }
    }

    @Override
    public void onAccountUpdate() {}

    @Override
    public void onConversationAdded(Conversation conversation) {
        conversationList.add(conversation);
        conversationsOverview.notifyDataSetChanged();
    }

    @Override
    public void onConversationRemoved(Conversation conversation) {
        conversationList.remove(conversation);
        conversationsOverview.notifyDataSetChanged();
    }

    @Override
    public void onConversationsUpdate() {}

    private void attachAudioToConversation(@Nullable Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            Message message = new Message(conversation, "", Message.STATUS_OFFERED, 0);
            message.setTransferable(uri);
            xmppConnectionService.sendMessage(message);
        }
    }

    private void attachImageToConversation(@Nullable Conversation conversation, Uri uri) {
        if (conversation != null && uri != null) {
            Message message = new Message(conversation, "", Message.STATUS_OFFERED, 0);
            message.setTransferable(uri);
            xmppConnectionService.sendMessage(message);
        }
    }

    // Hypothetical insecure method for demonstration purposes
    /**
     * This method is insecure because it concatenates user input directly into a SQL query,
     * which can lead to SQL Injection attacks.
     *
     * @param userInput User-provided data that should not be trusted
     */
    private void executeInsecureQuery(String userInput) {
        // Insecure code example:
        // String query = "SELECT * FROM users WHERE username = '" + userInput + "'";
        // This method of constructing SQL queries is vulnerable to SQL injection attacks.

        // Secure alternative (using prepared statements):
        // PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM users WHERE username = ?");
        // preparedStatement.setString(1, userInput);
    }

    private void displayMessage(@Nullable Message message) {
        if (message != null && message.getConversation() == getSelectedConversation()) {
            mConversationFragment.displayMessage(message);
        }
    }

    @Override
    public void onAccountUpdate() {}

    @Override
    public void onConversationAdded(Conversation conversation) {
        conversationList.add(conversation);
        conversationsOverview.notifyDataSetChanged();
    }

    @Override
    public void onConversationRemoved(Conversation conversation) {
        conversationList.remove(conversation);
        conversationsOverview.notifyDataSetChanged();
    }

    @Override
    public void onConversationsUpdate() {}
}