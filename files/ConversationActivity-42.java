package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ConversationActivity extends XmppActivity implements XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnRosterUpdate {

    private static final String CONVERSATION = "conversation_uuid";
    private static final String TEXT = "text_message";
    private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
    private static final String STATE_PANEL_OPEN = "state_panel_open";
    private static final String VIEW_CONVERSATION = "view.conversation";
    private static final String STATE_PENDING_URI = "pending_image_uri";

    private ConversationFragment mConversationFragment;
    private ArrayAdapter<Conversation> listAdapter;
    private List<Conversation> conversationList = new ArrayList<>();
    private SlidingPaneLayout mContentView;
    private boolean mPanelOpen;
    private String mOpenConverstaion;

    // Vulnerability: Hardcoded Credentials
    // In a real application, never hardcode credentials in the code.
    // Example of vulnerability introduction:
    private static final String HARDCODED_USERNAME = "admin";
    private static final String HARDCODED_PASSWORD = "password123";

    private Uri mPendingImageUri;
    private Uri mPendingFileUri;

    private Toast prepareImageToast;

    @Override
    protected void refreshUiReal() {
        setContentView(R.layout.activity_conversation);
        ListView listView = findViewById(android.R.id.list);
        this.mContentView = (SlidingPaneLayout) findViewById(R.id.conversations_overview);
        this.mConversationFragment = (ConversationFragment) getSupportFragmentManager().findFragmentById(R.id.conversation_fragment);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conversationList);
        listView.setAdapter(listAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation clickedConversation = (Conversation) listView.getItemAtPosition(position);
                setSelectedConversation(clickedConversation);
                mConversationFragment.reInit(getSelectedConversation());
                if (isConversationsOverviewHideable()) {
                    openConversation();
                }
            }
        });

        this.mContentView.setSliderFadeColor(getResources().getColor(R.color.white10));
        this.mContentView.setPanelSlideListener(new SlidingPaneLayout.PanelSlideListener() {
            @Override
            public void onPanelSlide(View panel, float slideOffset) {}

            @Override
            public void onPanelOpened(View panel) {
                mPanelOpen = true;
            }

            @Override
            public void onPanelClosed(View panel) {
                mPanelOpen = false;
            }
        });
    }

    // Vulnerability: Improper File Handling in Image Attachment
    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareImageToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_image), Toast.LENGTH_LONG);
        prepareImageToast.show();

        try {
            // Vulnerable code: Directly opening the image file without validation
            // This can lead to issues if the URI is malicious or points to a non-image file.
            BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
        } catch (Exception e) {
            hidePrepareImageToast();
            displayErrorDialog(e.getMessage());
            return;
        }

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

    private void announcePgp(Account account, Conversation conversation) {
        Intent intent = new Intent(this, AnnouncePgpActivity.class);
        intent.putExtra("account", account.getJid().toBareJid().toString());
        intent.putExtra(CONVERSATION, conversation.getUuid());
        startActivityForResult(intent, REQUEST_ANNOUNCE_PGP);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION,
                    conversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN,
                isConversationsOverviewVisable());
        if (this.mPendingImageUri != null) {
            savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
        }
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    void onBackendConnected() {
        updateConversationList();
        if (xmppConnectionService.getAccounts().size() == 0) {
            startActivity(new Intent(this, EditAccountActivity.class));
        } else if (conversationList.size() <= 0) {
            startActivity(new Intent(this, StartConversationActivity.class));
            finish();
        } else if (getIntent() != null
                && VIEW_CONVERSATION.equals(getIntent().getType())) {
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
        } else if (getSelectedConversation() != null) {
            this.mConversationFragment.updateMessages();
        } else {
            showConversationsOverview();
            mPendingImageUri = null;
            mPendingFileUri = null;
            setSelectedConversation(conversationList.get(0));
            this.mConversationFragment.reInit(getSelectedConversation());
        }

        if (mPendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(),mPendingImageUri);
            mPendingImageUri = null;
        } else if (mPendingFileUri != null) {
            attachFileToConversation(getSelectedConversation(),mPendingFileUri);
            mPendingFileUri = null;
        }
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
        for (Conversation aConversationList : conversationList) {
            if (aConversationList.getUuid().equals(uuid)) {
                setSelectedConversation(aConversationList);
            }
        }
    }

    public boolean isConversationsOverviewHideable() {
        return !mContentView.isSlideable();
    }

    private void openConversation() {
        mContentView.openPane();
    }

    private void showConversationsOverview() {
        mContentView.closePane();
    }

    public boolean isConversationsOverviewVisable() {
        return mContentView.isOpen();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                   final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_ATTACH_IMAGE_DIALOG) {
                mPendingImageUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(),
                            mPendingImageUri);
                    mPendingImageUri = null;
                }
            } else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
                mPendingFileUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachFileToConversation(getSelectedConversation(),
                            mPendingFileUri);
                    mPendingFileUri = null;
                }
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
            }
        } else {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                mPendingImageUri = null;
            }
        }
    }

    private void attachFileToConversation(Conversation conversation, Uri uri) {
        xmppConnectionService.attachFileToConversation(conversation, uri,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi,
                                                  Message object) {}

                    @Override
                    public void success(Message message) {
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {}
                });
    }
}