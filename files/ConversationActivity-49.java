// Class representing an activity for managing XMPP conversations in an Android app.
public class ConversationActivity extends Abstract XmppActivity {

    private Uri mPendingImageUri;
    private Uri mPendingFileUri;
    private String mOpenConverstaion = null;
    private boolean mPanelOpen = false;
    private Toast prepareFileToast;

    // Method to clear the notification service's open conversation reference.
    @Override
    protected void unregisterListeners() {
        super.unregisterListeners();
        xmppConnectionService.getNotificationService().setOpenConversation(null);
    }

    // Method handling activity result callbacks for various intents.
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
                    mPendingImageUri = data.getData();
                    if (xmppConnectionServiceBound) {
                        attachImageToConversation(getSelectedConversation(), mPendingImageUri);
                        mPendingImageUri = null;
                    }
                    break;
                case ATTACHMENT_CHOICE_CHOOSE_FILE:
                case ATTACHMENT_CHOICE_RECORD_VOICE:
                    mPendingFileUri = data.getData();
                    if (xmppConnectionServiceBound) {
                        attachFileToConversation(getSelectedConversation(), mPendingFileUri);
                        mPendingFileUri = null;
                    }
                    break;
                case ATTACHMENT_CHOICE_TAKE_PHOTO:
                    if (mPendingImageUri != null && xmppConnectionServiceBound) {
                        attachImageToConversation(getSelectedConversation(), mPendingImageUri);
                        mPendingImageUri = null;
                    }
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(mPendingImageUri);
                    sendBroadcast(intent);
                    break;
            }
        } else {
            if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                mPendingImageUri = null;
            }
        }
    }

    // Method to attach a file to the current conversation.
    private void attachFileToConversation(Conversation conversation, Uri uri) {
        prepareFileToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_file), Toast.LENGTH_LONG);
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
            public void userInputRequried(PendingIntent pi, Message message) {

            }
        });
    }

    // Method to attach an image to the current conversation.
    private void attachImageToConversation(Conversation conversation, Uri uri) {
        prepareFileToast = Toast.makeText(getApplicationContext(),
                getText(R.string.preparing_image), Toast.LENGTH_LONG);
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

    // Method to hide the prepare file toast.
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

    // Method to update the conversation list and refresh the UI.
    public void updateConversationList() {
        xmppConnectionService.populateWithOrderedConversations(conversationList);
        listAdapter.notifyDataSetChanged();
    }

    // Method to handle pending intents.
    public void runIntent(PendingIntent pi, int requestCode) {
        try {
            this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
                    null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
        }
    }

    // Method to encrypt a text message.
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

    // Method to check if encryption should be forced.
    public boolean forceEncryption() {
        return getPreferences().getBoolean("force_encryption", false);
    }

    // Method to check if the send button should indicate status.
    public boolean useSendButtonToIndicateStatus() {
        return getPreferences().getBoolean("send_button_status", false);
    }

    // Method to check if received messages should be indicated.
    public boolean indicateReceived() {
        return getPreferences().getBoolean("indicate_received", false);
    }

    // Method for refreshing the UI in a real-time manner.
    @Override
    protected void refreshUiReal() {
        updateConversationList();
        if (xmppConnectionService != null && xmppConnectionService.getAccounts().size() == 0) {
            if (!mRedirected) {
                this.mRedirected = true;
                startActivity(new Intent(this, EditAccountActivity.class));
                finish();
            }
        } else if (conversationList.size() == 0) {
            if (!mRedirected) {
                this.mRedirected = true;
                Intent intent = new Intent(this, StartConversationActivity.class);
                intent.putExtra("init",true);
                startActivity(intent);
                finish();
            }
        } else {
            ConversationActivity.this.mConversationFragment.updateMessages();
            updateActionBarTitle();
        }
    }

    // Method to refresh the UI on account update.
    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    // Method to refresh the UI on conversation update.
    @Override
    public void onConversationUpdate() {
        this.refreshUi();
    }

    // Method to refresh the UI on roster update.
    @Override
    public void onRosterUpdate() {
        this.refreshUi();
    }

    // Method to refresh the UI and invalidate options menu on blocklist status update.
    @Override
    public void OnUpdateBlocklist(Status status) {
        this.refreshUi();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                invalidateOptionsMenu();
            }
        });
    }

    // Method to send an unblock request for a conversation.
    public void unblockConversation(final Blockable conversation) {
        xmppConnectionService.sendUnblockRequest(conversation);
    }

    // Method to check if the enter key should be used as send button.
    public boolean enterIsSend() {
        return getPreferences().getBoolean("enter_is_send",false);
    }
}