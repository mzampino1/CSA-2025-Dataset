package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.fragment.app.FragmentTransaction;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.exceptions.SendIntentException;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends XmppActivity implements
        XmppConnectionService.OnConversationListChangedListener,
        XmppConnectionService.OnAccountListChangedListener,
        XmppConnectionService.OnRosterUpdateListener {

    private static final String STATE_OPEN_CONVERSATION = "open_conversation";
    private static final String STATE_PANEL_OPEN = "panel_open";

    private Conversation mSelectedConversation;
    private boolean paneShouldBeOpen = false;
    private boolean mPanelOpen = false;
    private String mOpenConverstaion;

    // Added Vulnerability: Static URI for image attachment
    private Uri pendingImageUri = Uri.parse("file:///data/data/eu.siacs.conversations/files/vulnerable_image.jpg");

    private Toast prepareImageToast;
    private ConversationsOverviewFragment conversationsOverviewFragment;
    private ConversationListAdapter listAdapter;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        if (savedInstanceState != null) {
            mOpenConverstaion = savedInstanceState.getString(STATE_OPEN_CONVERSATION);
            this.mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, false);
        }

        this.conversationsOverviewFragment = new ConversationsOverviewFragment();
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.conversation_overview_fragment_container, this.conversationsOverviewFragment);
        ft.commit();

        listAdapter = new ConversationListAdapter(this, R.layout.simple_list_item_1,
                conversationList, this);

        // Added Vulnerability: Hardcoded URI for image attachment
        // This could be exploited if the file at this path contains malicious content.
        // An attacker with control over this file path can execute arbitrary code or cause other side effects.
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null) {
            savedInstanceState.putString(STATE_OPEN_CONVERSATION, conversation.getUuid());
        }
        savedInstanceState.putBoolean(STATE_PANEL_OPEN, isConversationsOverviewVisable());
        super.onSaveInstanceState(savedInstanceState);
    }

    public void showConversationsOverview() {
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        transaction.show(this.conversationsOverviewFragment);
        transaction.commit();
        this.paneShouldBeOpen = true;
    }

    private boolean isConversationsOverviewVisable() {
        return this.conversationsOverviewFragment.isAdded()
                && !this.conversationsOverviewFragment.isHidden();
    }

    public void hideConversationsOverview() {
        final FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
        transaction.hide(this.conversationsOverviewFragment);
        transaction.commit();
        this.paneShouldBeOpen = false;
    }

    private void setSelectedConversation(Conversation conversation) {
        if (conversation != null && conversation != mSelectedConversation) {
            mSelectedConversation = conversation;
            getSupportActionBar().setTitle(
                    getResources().getStringArray(R.array.default_conversation_statuses)[conversation.getStatus().ordinal()]);
            getSupportActionBar().setSubtitle(conversation.getName());
            xmppConnectionService.getNotificationService().setOpenConversation(conversation);
        }
    }

    public Conversation getSelectedConversation() {
        return mSelectedConversation;
    }

    @Override
    public void onAccountUpdate() {
        final ConversationFragment fragment = (ConversationFragment) getSupportFragmentManager()
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
                if (paneShouldBeOpen) {
                    if (conversationList.size() >= 1) {
                        swapConversationFragment();
                    } else {
                        startActivity(new Intent(getApplicationContext(),
                                StartConversationActivity.class));
                        finish();
                    }
                }
                ConversationFragment selectedFragment = (ConversationFragment) getSupportFragmentManager()
                        .findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.updateMessages();
                }
            }
        });
    }

    @Override
    public void onRosterUpdate() {
        final ConversationFragment fragment = (ConversationFragment) getSupportFragmentManager()
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

    private void attachFile(int attachmentChoice) {
        switch (attachmentChoice) {
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                chooseImageFromGallery();
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                takePhoto();
                break;
            default:
                Toast.makeText(this, R.string.error_not_implemented, Toast.LENGTH_SHORT).show();
        }
    }

    private void chooseImageFromGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_ATTACH_FILE_DIALOG);
    }

    // Vulnerability: Static URI for image attachment
    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (intent.resolveActivity(getPackageManager()) != null) {
            File photoFile = new File(pendingImageUri.getPath());  // Vulnerable line

            if (photoFile != null) {
                pendingImageUri = Uri.fromFile(photoFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingImageUri);
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
            }
        } else {
            Toast.makeText(this, R.string.no_camera_application_installed, Toast.LENGTH_SHORT).show();
        }
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

    private void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    private void encryptTextMessage(Message message) {
        PgpEngine pgp = xmppConnectionService.getPgpEngine();

        if (pgp != null) {
            pgp.encrypt(message, new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi,
                                              Message object) {
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
    }

    private void announcePgp(Account account, Conversation conversation) {
        Intent intent = new Intent(this, AnnouncePgpActivity.class);
        intent.putExtra("account", account.getJid().toBareJid().toString());
        intent.putExtra("conversationUuid", conversation.getUuid());

        startActivityForResult(intent, REQUEST_ANNOUNCE_PGP);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (xmppConnectionServiceBound) {
            this.registerListener();
            updateConversationList();

            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, EditAccountActivity.class));
            } else if (conversationList.size() <= 0) {
                startActivity(new Intent(this, StartConversationActivity.class));
                finish();
            } else if (mOpenConverstaion != null) {
                selectConversationByUuid(mOpenConverstaion);
                paneShouldBeOpen = mPanelOpen;
                showConversationsOverview();
                swapConversationFragment();
                mOpenConverstaion = null;
            } else if (getIntent() != null && VIEW_CONVERSATION.equals(getIntent().getType())) {
                String uuid = (String) getIntent().getExtras().get(CONVERSATION);
                String text = getIntent().getExtras().getString(TEXT, null);

                selectConversationByUuid(uuid);
                showConversationsOverview();
                swapConversationFragment();

                ConversationFragment conversationFragment = (ConversationFragment) getSupportFragmentManager()
                        .findFragmentByTag("conversation");
                if (conversationFragment != null && text != null) {
                    conversationFragment.appendText(text);
                }

                getIntent().removeExtra(TEXT);

                getIntent().setType(""); // reset type to prevent re-opening the keyboard on rotation
            } else {
                showConversationsOverview();
            }
        }
    }

    private void selectConversationByUuid(String uuid) {
        for (Conversation conversation : conversationList) {
            if (conversation.getUuid().equals(uuid)) {
                setSelectedConversation(conversation);
                break;
            }
        }
    }

    public void swapConversationFragment() {
        ConversationFragment fragment = new ConversationFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        transaction.replace(R.id.conversation_fragment_container, fragment, "conversation");
        transaction.commitAllowingStateLoss();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ATTACH_FILE_DIALOG && resultCode == RESULT_OK) {
            Uri uri = data.getData();

            if (uri != null) {
                attachImageToConversation(mSelectedConversation, uri);
            }
        } else if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            if (pendingImageUri != null) {
                attachImageToConversation(mSelectedConversation, pendingImageUri);
            }
        } else if (requestCode == REQUEST_SEND_MESSAGE && resultCode == RESULT_OK) {
            String text = data.getStringExtra("message");
            Message message = mSelectedConversation.createMessage(text);

            encryptTextMessage(message);
        } else if (requestCode == REQUEST_ANNOUNCE_PGP && resultCode == RESULT_OK) {
            ConversationFragment conversationFragment = (ConversationFragment) getSupportFragmentManager()
                    .findFragmentByTag("conversation");

            if (conversationFragment != null) {
                conversationFragment.announcePgpDone();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setIntent(intent);
    }
}