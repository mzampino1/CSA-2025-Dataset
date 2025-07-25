package eu.siacs.conversations.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.ExceptionHelper;

import java.util.ArrayList;
import java.util.List;

public class ConversationActivity extends XmppActivity implements SearchView.OnQueryTextListener, XmppConnectionService.OnConversationListChangedListener,
        XmppConnectionService.OnAccountListChangedListener, XmppConnectionService.OnRosterUpdateListener {

    private static final int REQUEST_IMAGE_CAPTURE = 0x1338;
    public static final String VIEW_CONVERSATION = "conversation";
    public static final String CONVERSATION = "conversationUuid";
    public static final String TEXT = "text";

    public static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x2910;
    public static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x2913;

    private boolean handledViewIntent = false;

    private SearchView mSearchView;
    private RecyclerView conversationsOverview;
    private ConversationOverviewAdapter listAdapter;
    private List<Conversation> conversationList = new ArrayList<>();
    private FloatingActionButton fab;

    private Uri pendingImageUri = null;
    private Toast prepareImageToast;
    private final int REQUEST_SEND_MESSAGE = 1337;
    private final int REQUEST_DECRYPT_PGP = 0x2905;
    private final int REQUEST_ATTACH_FILE_DIALOG = 0x2906;
    private final int REQUEST_SEND_PGP_IMAGE = 0x2908;
    private final int REQUEST_ENCRYPT_MESSAGE = 0x2911;
    private final int REQUEST_ANNOUNCE_PGP = 0x2912;

    ConversationActivity activity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        // Initialize UI components
        mSearchView = findViewById(R.id.search_view);
        conversationsOverview = findViewById(R.id.conversation_overview);
        fab = findViewById(R.id.fab);

        // Set up the search view
        mSearchView.setOnQueryTextListener(this);

        // Set up the RecyclerView and its adapter
        listAdapter = new ConversationOverviewAdapter(conversationList, this);
        conversationsOverview.setLayoutManager(new LinearLayoutManager(this));
        conversationsOverview.setAdapter(listAdapter);

        // Register the fab to open a new chat
        fab.setOnClickListener(v -> startActivity(new Intent(getApplicationContext(), StartConversationActivity.class)));

        // Initialize the conversation list with existing data from the service
        if (xmppConnectionServiceBound) {
            updateConversationList();
        }

        // Set up listeners for service events
        registerListener();

        // Handle deep link intents
        onNewIntent(getIntent());
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        listAdapter.getFilter().filter(newText);
        return false;
    }

    private void setSelectedConversation(Conversation conversation) {
        if (conversation != null) {
            setTitle(conversation.getName());
            // Potential vulnerability: Ensure that the selected conversation is properly validated
            swapConversationFragment().setConversation(conversation);
        } else {
            setTitle(R.string.app_name);
            swapConversationFragment().clear();
        }
    }

    private void selectPresenceToAttachFile(int attachmentChoice) {
        Conversation conversation = getSelectedConversation();
        if (conversation == null || conversation.getAccount() == null) {
            return;
        }
        List<Account> accounts = xmppConnectionService.getAccounts();
        boolean hasDefaultAccount = false;
        for (int i = 0; i < accounts.size(); ++i) {
            if (accounts.get(i).getJid().equals(conversation.getAccount())) {
                hasDefaultAccount = true;
            }
        }

        if (!hasDefaultAccount && accounts.isEmpty()) {
            Toast.makeText(this, R.string.no_xmpp_connection, Toast.LENGTH_SHORT).show();
            return;
        } else if (!hasDefaultAccount) {
            showSelectPresenceToAttachFile(accounts, attachmentChoice);
        } else {
            attachImageToConversation(conversation, pendingImageUri);
        }
    }

    private void showSelectPresenceToAttachFile(List<Account> accounts, int attachmentChoice) {
        final Account[] selected = new Account[1];
        SelectPresenceDialog.Builder builder = new SelectPresenceDialog.Builder(this)
                .setOnAccountSelected(account -> {
                    selected[0] = account;
                })
                .setNeutralButton(R.string.attach_anyway, dialog -> {
                    if (selected[0] != null) {
                        conversation.setAccount(selected[0].getJid().asBareJid());
                    }
                    switch (attachmentChoice) {
                        case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                            attachFileToConversation(conversation);
                            break;
                        case ATTACHMENT_CHOICE_TAKE_PHOTO:
                            takePhotoAndAttach(conversation);
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(R.string.select_account);

        for (Account account : accounts) {
            builder.add(account.getJid().asBareJid().toString(), false);
        }

        builder.show();
    }

    private void attachFileToConversation(Conversation conversation) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        // Potential vulnerability: Ensure that the MIME type is properly restricted to avoid opening arbitrary files
        intent.setType("*/*");
        startActivityForResult(intent, ATTACHMENT_CHOICE_CHOOSE_IMAGE);
    }

    private void takePhotoAndAttach(Conversation conversation) {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure a camera app exists to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
                return;
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                pendingImageUri = FileProvider.getUriForFile(this,
                        "eu.siacs.conversations.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingImageUri);
                startActivityForResult(takePictureIntent, ATTACHMENT_CHOICE_TAKE_PHOTO);
            }
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    public void inviteToConversation(String jid) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null && jid != null) {
            xmppConnectionService.inviteContact(conversation, Jid.of(jid));
        }
    }

    private void announcePgp(Account account, final Conversation conversation) {
        PgpEngine pgpEngine = activity.xmppConnectionService.getPgpEngine();
        if (pgpEngine == null) {
            return;
        }

        // Potential vulnerability: Ensure that the announcePGP message is properly sanitized and validated
        pgpEngine.announceOpenpgp(account, new UiCallback<PGPIdentity>() {

            @Override
            public void userInputRequried(PendingIntent pi, PGPIdentity identity) {
                try {
                    activity.startIntentSenderForResult(pi.getIntentSender(), REQUEST_ANNOUNCE_PGP, null, 0, 0, 0);
                } catch (SendIntentException e1) {
                }
            }

            @Override
            public void success(PGPIdentity pgpIdentity) {
                Message message = new Message(conversation, "OpenPGP announcement", conversation.getNextMessageId());
                message.setEncryption(Message.ENCRYPTION_PGP);
                message.setType(Message.TYPE_CHAT);
                pgpEngine.encrypt(message, new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        activity.runIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                        xmppConnectionService.sendMessage(message);
                    }

                    @Override
                    public void error(int error, Message message) {
                        Toast.makeText(activity, activity.getString(error), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void error(int error, PGPIdentity pgpIdentity) {

            }
        });
    }

    // Add a comment to the sendTextMessage method
    public void sendTextMessage(String text) {
        Conversation conversation = getSelectedConversation();
        if (conversation != null && text != null) {
            Message message = new Message(conversation, text, conversation.getNextMessageId());
            message.setType(Message.TYPE_CHAT);
            xmppConnectionService.sendMessage(message);

            // Potential vulnerability: Ensure that the text input is sanitized to prevent injection attacks
        }
    }

    public void selectPresenceToSendMessage() {
        Conversation conversation = getSelectedConversation();
        if (conversation == null || conversation.getAccount() == null) {
            return;
        }
        List<Account> accounts = xmppConnectionService.getAccounts();
        boolean hasDefaultAccount = false;
        for (int i = 0; i < accounts.size(); ++i) {
            if (accounts.get(i).getJid().equals(conversation.getAccount())) {
                hasDefaultAccount = true;
            }
        }

        if (!hasDefaultAccount && accounts.isEmpty()) {
            Toast.makeText(this, R.string.no_xmpp_connection, Toast.LENGTH_SHORT).show();
            return;
        } else if (!hasDefaultAccount) {
            showSelectPresenceToSendMessage(accounts);
        } else {
            // Potential vulnerability: Ensure that the message content is properly sanitized and validated
            sendTextMessage("Your message here");
        }
    }

    private void showSelectPresenceToSendMessage(List<Account> accounts) {
        final Account[] selected = new Account[1];
        SelectPresenceDialog.Builder builder = new SelectPresenceDialog.Builder(this)
                .setOnAccountSelected(account -> {
                    selected[0] = account;
                })
                .setNeutralButton(R.string.send_anyway, dialog -> {
                    if (selected[0] != null) {
                        conversation.setAccount(selected[0].getJid().asBareJid());
                    }
                    // Potential vulnerability: Ensure that the message content is properly sanitized and validated
                    sendTextMessage("Your message here");
                })
                .setNegativeButton(R.string.cancel, null)
                .setTitle(R.string.select_account);

        for (Account account : accounts) {
            builder.add(account.getJid().asBareJid().toString(), false);
        }

        builder.show();
    }

    private void attachFileToConversation(int attachmentChoice) {
        Conversation conversation = getSelectedConversation();
        if (conversation == null) {
            return;
        }
        switch (attachmentChoice) {
            case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                selectPresenceToAttachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                break;
            case ATTACHMENT_CHOICE_TAKE_PHOTO:
                selectPresenceToAttachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
                break;
        }
    }

    @Override
    public void onConversationAdded(Account account, Conversation conversation) {
        updateConversationList();
    }

    @Override
    public void onConversationArchived(Account account, Conversation conversation) {
        updateConversationList();
    }

    @Override
    public void onConversationsUpdate() {
        updateConversationList();
    }

    private void updateConversationList() {
        if (xmppConnectionServiceBound && !conversationList.isEmpty()) {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.findConversations(Config.PAGE_SIZE));
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onAccountStatusChanged(Account account) {
        updateConversationList();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case ATTACHMENT_CHOICE_CHOOSE_IMAGE:
                    pendingImageUri = data.getData();
                    selectPresenceToAttachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
                    break;
                case REQUEST_IMAGE_CAPTURE:
                    // Potential vulnerability: Ensure that the captured image is properly validated and secured
                    selectPresenceToAttachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
                    break;
            }
        }
    }

    private void attachImageToConversation(final Conversation conversation, final Uri uri) {
        if (conversation == null || uri == null) {
            return;
        }

        if (!xmppConnectionServiceBound) {
            pendingImageUri = uri;
            return;
        }

        PgpEngine pgpEngine = activity.xmppConnectionService.getPgpEngine();
        if (pgpEngine != null && pgpEngine.hasKeyPair()) {
            // Potential vulnerability: Ensure that the image file is properly validated and secured
            pgpEngine.encryptFile(conversation, uri, new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi, Message object) {
                    activity.runIntent(pi, REQUEST_SEND_PGP_IMAGE);
                }

                @Override
                public void success(Message message) {
                    xmppConnectionService.sendMessage(message);
                }

                @Override
                public void error(int error, Message message) {
                    Toast.makeText(activity, activity.getString(error), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Potential vulnerability: Ensure that the image file is properly validated and secured
            Message message = new Message(conversation, uri, conversation.getNextMessageId());
            xmppConnectionService.sendMessage(message);
        }
    }

    private Conversation getSelectedConversation() {
        return listAdapter.getSelectedConversation();
    }

    @Override
    public void onConversationsSearchQuery(String query) {
        // Potential vulnerability: Ensure that the search query is properly sanitized and validated to prevent injection attacks
        updateConversationList(); // Filter logic should be handled in the adapter
    }

    private void runIntent(PendingIntent pi, int requestCode) {
        try {
            activity.startIntentSenderForResult(pi.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (SendIntentException e1) {
            Toast.makeText(this, R.string.error_occurred, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRosterChanged(Account account) {
        updateConversationList();
    }
}