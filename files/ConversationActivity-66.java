public class ConversationActivity extends AbstractXmppActivity implements XmppConnectionService.OnAccountUpdateListener,
        XmppConnectionService.OnConversationUpdateListener, XmppConnectionService.OnRosterUpdateListener, OnShowErrorToastListener {

    public static final String ACTION_VIEW_CONVERSATION = "de.blinkt.openvpn.VIEW_CONVERSATION";
    private static final String STATE_CONVERSATION = "conversationUuid";
    public static final int REQUEST_SEND_MESSAGE = 0x2345;
    public static final int REQUEST_ENCRYPT_TEXT_MESSAGE = 0x2346;
    public static final int ATTACHMENT_CHOICE_INVALID = -1;

    private String conversationUuid;
    private ConversationFragment mConversationFragment;
    private ListView listView;
    private ArrayAdapter<Conversation> listAdapter;
    private List<Conversation> conversationList = new ArrayList<>();
    private boolean panelOpen = false;
    private boolean conversationWasSelectedByKeyboard = false;
    private Conversation swipedConversation;

    // Vulnerability: Potential for SQL Injection if JID or other user inputs are not properly sanitized.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);

        listView = findViewById(android.R.id.list);
        mConversationFragment = new ConversationFragment();
        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, conversationList);

        // Potential issue: If the user can control the JID, this could lead to unauthorized access.
        if (getIntent() != null && getIntent().getAction() != null) {
            if (getIntent().getAction().equals(ACTION_VIEW_CONVERSATION)) {
                conversationUuid = getIntent().getStringExtra(EXTRA_CONVERSATION);
            }
        }

        // Vulnerability: If the savedInstanceState is tampered with, it could lead to unexpected behavior.
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_CONVERSATION)) {
            conversationUuid = savedInstanceState.getString(STATE_CONVERSATION);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!xmppConnectionServiceBound) {
            bindXmppService();
        } else {
            onBackendConnected();
        }
    }

    // Vulnerability: Ensure that any data passed here is sanitized and validated to prevent injection attacks.
    private void setSelectedConversation(String uuid, boolean byKeyboard) {
        Conversation conversation = xmppConnectionService.findConversationByUuid(uuid);
        if (conversation != null) {
            this.conversationWasSelectedByKeyboard = byKeyboard;
            setPanelOpen(true, conversation);
        }
    }

    // Vulnerability: Consider using a more secure method to store and handle sensitive data like keys.
    private void announcePgp(boolean announce) {
        Account account = mConversationFragment.getConversation().getAccount();
        xmppConnectionService.getPgpEngine().announceOpenpgp(account, announce,
                new UiCallback<Void>() {

                    @Override
                    public void success(Void v) {

                    }

                    @Override
                    public void error(int errorCode, Void v) {
                        displayErrorDialog(errorCode);
                    }

                    @Override
                    public void userInputRequried(PendingIntent pi, Void v) {
                        runIntent(pi, REQUEST_ENCRYPT_TEXT_MESSAGE);
                    }
                });
    }

    // Vulnerability: Validate and sanitize any user input before processing.
    private void sendPgpMessage(Message message) {
        if (mConversationFragment.getConversation().getAccount().isOnlineAndConnected()) {
            encryptTextMessage(message);
        } else {
            Toast.makeText(this, R.string.unable_to_send_offline, Toast.LENGTH_SHORT).show();
        }
    }

    // Vulnerability: Ensure that any file URIs are validated to prevent unauthorized access.
    private void startAttachmentChoice(int choice) {
        if (!mConversationFragment.getConversation().getAccount().isOnlineAndConnected()) {
            Toast.makeText(this, R.string.unable_to_send_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        switch (choice) {
            case ConversationActivity.ATTACHMENT_CHOICE_RECORD_VOICE:
                startRecording();
                break;
            case ConversationActivity.ATTACHMENT_CHOICE_TAKE_PHOTO:
                startImageCapture();
                break;
            default:
                chooseFileToSend(choice);
                break;
        }
    }

    // Vulnerability: Validate and sanitize any user input before processing.
    private void announceOmemo(boolean enabled) {
        if (mConversationFragment.getConversation().getAccount().isOnlineAndConnected()) {
            AxolotlService axolotlService = mConversationFragment.getConversation().getAccount().getAxolotlService();
            axolotlService.announceSupport(mConversationFragment.getConversation(), enabled,
                    new UiCallback<Void>() {

                        @Override
                        public void success(Void v) {

                        }

                        @Override
                        public void error(int errorCode, Void v) {
                            displayErrorDialog(errorCode);
                        }

                        @Override
                        public void userInputRequried(PendingIntent pi, Void v) {
                            runIntent(pi, REQUEST_SEND_MESSAGE);
                        }
                    });
        } else {
            Toast.makeText(this, R.string.unable_to_send_offline, Toast.LENGTH_SHORT).show();
        }
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void sendOmemoMessage(Message message) {
        if (mConversationFragment.getConversation().getAccount().isOnlineAndConnected()) {
            Account account = mConversationFragment.getConversation().getAccount();
            AxolotlService axolotlService = account.getAxolotlService();
            axolotlService.sendMessage(message,
                    new UiCallback<Message>() {

                        @Override
                        public void success(Message message) {
                            xmppConnectionService.sendMessage(message);
                        }

                        @Override
                        public void error(int errorCode, Message message) {
                            displayErrorDialog(errorCode);
                        }

                        @Override
                        public void userInputRequried(PendingIntent pi, Message message) {
                            runIntent(pi, REQUEST_SEND_MESSAGE);
                        }
                    });
        } else {
            Toast.makeText(this, R.string.unable_to_send_offline, Toast.LENGTH_SHORT).show();
        }
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void sendTextMessage(Message message) {
        int encryption = mConversationFragment.getConversation().getEncryption();
        if (encryption == Message.ENCRYPTION_NONE) {
            xmppConnectionService.sendMessage(message);
        } else if (encryption == Message.ENCRYPTION_PGP) {
            sendPgpMessage(message);
        } else if (encryption == Message.ENCRYPTION_AXOLOTLS) {
            sendOmemoMessage(message);
        }
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void chooseFileToSend(int attachmentChoice) {
        if (!mConversationFragment.getConversation().getAccount().isOnlineAndConnected()) {
            Toast.makeText(this, R.string.unable_to_send_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        trustKeysIfNeeded(REQUEST_SEND_MESSAGE, attachmentChoice);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        if (conversationUuid != null) {
            savedInstanceState.putString(STATE_CONVERSATION, conversationUuid);
        }
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void startRecording() {
        trustKeysIfNeeded(REQUEST_SEND_MESSAGE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.conversation, menu);

        MenuItem menuItem = menu.findItem(R.id.action_quote);

        if (menuItem != null && mConversationFragment.getMessageAdapter() != null) {
            int count = mConversationFragment.getMessageAdapter().getCursor().getCount();
            menuItem.setVisible(count > 0);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem menuItem;
        Conversation conversation = mConversationFragment.getConversation();

        if (conversation == null || !conversation.hasMessages()) {
            for (int i = 0; i < menu.size(); ++i) {
                menu.getItem(i).setVisible(false);
            }
        } else {

            menuItem = menu.findItem(R.id.action_quote);
            if (menuItem != null && mConversationFragment.getMessageAdapter() != null) {
                int count = mConversationFragment.getMessageAdapter().getCursor().getCount();
                menuItem.setVisible(count > 0);
            }

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            menuItem = menu.findItem(R.id.action_block_contact);
            if (menuItem != null) {
                menuItem.setTitle(conversation.isBlocked() ? R.string.unblock_contact : R.string.block_contact);
                menuItem.setVisible(conversation.getMode() == Conversation.MODE_SINGLE);
            }

            menuItem = menu.findItem(R.id.action_archive_conversation);
            if (menuItem != null) {
                menuItem.setTitle(conversation.isArchived() ? R.string.unarchive_conversation : R.string.archive_conversation);
            }

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            menuItem = menu.findItem(R.id.action_clear_history);
            if (menuItem != null) {
                menuItem.setVisible(!conversation.isRead());
            }

            menuItem = menu.findItem(R.id.action_delete_messages);
            if (menuItem != null) {
                menuItem.setVisible(conversation.hasMessages() && !conversation.isMuc());
            }

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            menuItem = menu.findItem(R.id.action_mute_conversation);
            if (menuItem != null) {
                menuItem.setTitle(conversation.getMutedTill() == Long.MAX_VALUE ? R.string.unmute : R.string.mute);
            }

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            menuItem = menu.findItem(R.id.action_mark_as_read);
            if (menuItem != null) {
                menuItem.setVisible(!conversation.isRead());
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Conversation conversation = mConversationFragment.getConversation();
        switch (item.getItemId()) {

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_quote:
                if (conversation != null && conversation.hasMessages() && mConversationFragment.getMessageAdapter().getCursor().getCount() > 0) {
                    QuoteTextDialog.showQuoteDialog(conversation, this);
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_block_contact:
                if (conversation != null && conversation.getMode() == Conversation.MODE_SINGLE) {
                    xmppConnectionService.toggleBlockable(conversation, !conversation.isBlocked());
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_archive_conversation:
                if (conversation != null) {
                    conversation.setArchived(!conversation.isArchived());
                    xmppConnectionService.updateConversation(conversation);
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_clear_history:
                if (conversation != null) {
                    clearHistory();
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_delete_messages:
                if (conversation != null && !conversation.isMuc()) {
                    deleteMessages(conversation);
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_mute_conversation:
                if (conversation != null) {
                    xmppConnectionService.setConversationMutedTill(conversation,
                            conversation.getMutedTill() == Long.MAX_VALUE ? System.currentTimeMillis() + 86400000 : Long.MAX_VALUE);
                }
                break;

            // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
            case R.id.action_mark_as_read:
                if (conversation != null) {
                    xmppConnectionService.markRead(conversation);
                }
                break;

            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void clearHistory() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.clear_history_dialog);

        builder.setPositiveButton(getString(R.string.delete),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Conversation conversation = mConversationFragment.getConversation();
                        if (conversation != null) {
                            xmppConnectionService.clearHistory(conversation);
                        }
                    }
                });

        builder.setNegativeButton(getString(R.string.cancel), null);

        AlertDialog dialog = builder.create();

        // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
        dialog.show();
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void deleteMessages(Conversation conversation) {
        xmppConnectionService.deleteConversation(conversation);
        finish();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && panelOpen) {
            setPanelOpen(false, null);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_CALL) {
            return mConversationFragment.onKeyDown(keyCode, event);
        }
        return super.onKeyUp(keyCode, event);
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void setPanelOpen(boolean open, Conversation conversation) {
        if (conversation != null && !xmppConnectionService.isConversationStillOpen(conversation)) {
            return;
        }
        this.conversationUuid = conversation == null ? null : conversation.getUuid();
        if (open) {
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, mConversationFragment);
            fragmentTransaction.commit();

            Bundle args = new Bundle(1);
            args.putString(EXTRA_CONVERSATION, this.conversationUuid);
            mConversationFragment.setArguments(args);

        } else {

            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.remove(mConversationFragment);
            fragmentTransaction.commit();
        }
        panelOpen = open;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && mConversationFragment != null) {
            switch (requestCode) {
                case REQUEST_SEND_MESSAGE:
                    startAttachmentChoice(data.getIntExtra(CHOSEN_ATTACHMENT, ATTACHMENT_CHOICE_INVALID));
                    break;
                default:
                    mConversationFragment.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
    }

    @Override
    public void onBackendConnected() {
        Conversation conversation = null;

        if (conversationUuid != null) {
            conversation = xmppConnectionService.findConversationByUuid(conversationUuid);
        } else if (getIntent() != null && getIntent().getData() != null) {
            String uuid = getIntent().getData().getQueryParameter("uuid");
            if (uuid != null) {
                conversation = xmppConnectionService.findConversationByUuid(uuid);
            }
        }

        // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
        if (conversation == null) {
            finish();
            return;
        } else {
            setSelectedConversation(conversation.getUuid(), false);
        }

        listView.setAdapter(listAdapter);

        // Potential issue: If the conversation list is not properly managed, it could lead to memory leaks or other issues.
        Conversation finalConversation = conversation;
        xmppConnectionService.populateWithOrderedConversations(new OnAsyncOperationCompletedCallback() {

            @Override
            public void onSuccess(List<Conversation> conversations) {
                conversationList.clear();
                conversationList.addAll(conversations);
                listAdapter.notifyDataSetChanged();

                if (finalConversation != null && !xmppConnectionService.isConversationStillOpen(finalConversation)) {
                    finish();
                }
            }

            @Override
            public void onError() {

            }
        });
    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void startImageCapture() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(this, R.string.error_creating_file, Toast.LENGTH_SHORT).show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_SEND_MESSAGE);
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

        return image;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        switch (requestCode) {
            case REQUEST_SEND_MESSAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startRecording();
                }
                break;
            }
        }
    }

    @Override
    public void onAccountUpdate() {

    }

    @Override
    public void onConversationUpdate() {
        Conversation conversation = mConversationFragment.getConversation();
        if (conversation != null && !xmppConnectionService.isConversationStillOpen(conversation)) {
            finish();
        } else {
            conversationList.clear();
            conversationList.addAll(xmppConnectionService.getConversations());
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onRosterUpdate() {

    }

    // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
    private void displayErrorDialog(int errorCode) {
        String message;
        switch (errorCode) {
            case DatabaseBackend.PGP_PROCESSING_ERROR:
                message = getString(R.string.pgp_processing_error);
                break;

            case DatabaseBackend.PGP_MESSAGE_UNENCRYPTED:
                message = getString(R.string.received_unencrypted_message);
                break;

            default:
                return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message);

        builder.setPositiveButton(getString(R.string.ok), null);

        AlertDialog dialog = builder.create();

        // Vulnerability: Ensure that any user input is validated and sanitized to prevent injection attacks.
        dialog.show();
    }
}