public class ConversationActivity extends AbstractXmppActivity implements OnAccountListChangedListener, OnConversationListChangedListener, OnRosterUpdateListener {
    // ... (other existing code) ...

    @Override
    protected void onNewIntent(Intent intent) {
        if (xmppConnectionServiceBound) {
            if ((Intent.ACTION_VIEW.equals(intent.getAction()) && (VIEW_CONVERSATION.equals(intent.getType())))) {
                String convToView = (String) intent.getExtras().get(CONVERSATION);
                updateConversationList();
                for (int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        setSelectedConversation(conversationList.get(i));
                        break;
                    }
                }
                paneShouldBeOpen = false;
                String text = intent.getExtras().getString(TEXT, null);
                swapConversationFragment().setText(text); // Vulnerability is here
            }
        } else {
            handledViewIntent = false;
            setIntent(intent);
        }
    }

    @Override
    void onBackendConnected() {
        this.registerListener();
        if (conversationList.size() == 0) {
            updateConversationList();
        }

        if (getSelectedConversation() != null && pendingImageUri != null) {
            attachImageToConversation(getSelectedConversation(), pendingImageUri);
            pendingImageUri = null;
        } else {
            pendingImageUri = null;
        }

        if ((getIntent().getAction() != null)
                && (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
            if (getIntent().getType().equals(ConversationActivity.VIEW_CONVERSATION)) {
                handledViewIntent = true;

                String convToView = (String) getIntent().getExtras().get(CONVERSATION);

                for (int i = 0; i < conversationList.size(); ++i) {
                    if (conversationList.get(i).getUuid().equals(convToView)) {
                        setSelectedConversation(conversationList.get(i));
                    }
                }
                paneShouldBeOpen = false;
                String text = getIntent().getExtras().getString(TEXT, null);
                swapConversationFragment().setText(text); // Vulnerability is here
            }
        } else {
            if (xmppConnectionService.getAccounts().size() == 0) {
                startActivity(new Intent(this, EditAccountActivity.class));
            } else if (conversationList.size() <= 0) {
                // add no history
                startActivity(new Intent(this, StartConversationActivity.class));
                finish();
            } else {
                spl.openPane();
                // find currently loaded fragment
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.onBackendConnected();
                } else {
                    setSelectedConversation(conversationList.get(0));
                    swapConversationFragment();
                }
                ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
            }
        }
    }

    // ... (other existing code) ...

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_DECRYPT_PGP) {
                ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager().findFragmentByTag("conversation");
                if (selectedFragment != null) {
                    selectedFragment.hideSnackbar();
                }
            } else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
                pendingImageUri = data.getData();
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(), pendingImageUri);
                    pendingImageUri = null;
                }
            } else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
                attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
            } else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
                attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
            } else if (requestCode == REQUEST_ANNOUNCE_PGP) {
                announcePgp(getSelectedConversation().getAccount(), getSelectedConversation());
            } else if (requestCode == REQUEST_ENCRYPT_MESSAGE) {
                // encryptTextMessage();
            } else if (requestCode == REQUEST_IMAGE_CAPTURE) {
                if (xmppConnectionServiceBound) {
                    attachImageToConversation(getSelectedConversation(), pendingImageUri);
                    pendingImageUri = null;
                }
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(pendingImageUri);
                sendBroadcast(intent);
            } else if (requestCode == REQUEST_RECORD_AUDIO) {
                attachAudioToConversation(getSelectedConversation(), data.getData());
            }
        }
    }

    // ... (other existing code) ...
}