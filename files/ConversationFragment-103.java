public class ConversationFragment extends Fragment implements OnEnterPressedListener {
    private Conversation conversation;
    // ... other fields ...

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Ensure all necessary permissions are checked here
    }

    protected void appendText(String text) {
        if (text == null || text.trim().isEmpty()) { // Validate input
            return;
        }
        String previous = this.binding.textinput.getText().toString();
        if (!previous.endsWith(" ")) {
            text = " " + text; // Ensure proper formatting
        }
        this.binding.textinput.append(text);
    }

    @Override
    public boolean onEnterPressed() {
        SharedPreferences p = PreferenceManager.getDefaultSharedPreferences(getActivity());
        final boolean enterIsSend = p.getBoolean("enter_is_send", getResources().getBoolean(R.bool.enter_is_send));
        if (enterIsSend) {
            // Validate message content here if necessary
            sendMessage();
            return true;
        } else {
            return false;
        }
    }

    protected void sendPgpMessage(final Message message) {
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog();
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation, null, activity.onOpenPGPKeyPublished);
            return;
        }
        if (!mSendingPgpMessage.compareAndSet(false, true)) {
            Log.d(Config.LOGTAG, "sending pgp message already in progress");
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact,
                        new UiCallback<Contact>() {

                            @Override
                            public void userInputRequried(PendingIntent pi, Contact contact) {
                                startPendingIntent(pi, REQUEST_ENCRYPT_MESSAGE);
                            }

                            @Override
                            public void success(Contact contact) {
                                encryptTextMessage(message);
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                activity.runOnUiThread(() -> Toast.makeText(activity,
                                        R.string.unable_to_connect_to_keychain,
                                        Toast.LENGTH_SHORT
                                ).show());
                                mSendingPgpMessage.set(false);
                            }
                        });

            } else {
                showNoPGPKeyDialog(false, (dialog, which) -> {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    xmppService.updateConversation(conversation);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.sendMessage(message);
                    messageSent();
                });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = Toast
                            .makeText(getActivity(),
                                    R.string.missing_public_keys,
                                    Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                encryptTextMessage(message);
            } else {
                showNoPGPKeyDialog(true, (dialog, which) -> {
                    conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                    message.setEncryption(Message.ENCRYPTION_NONE);
                    xmppService.updateConversation(conversation);
                    xmppService.sendMessage(message);
                    messageSent();
                });
            }
        }
    }

    public void encryptTextMessage(Message message) {
        activity.xmppConnectionService.getPgpEngine().encrypt(message,
                new UiCallback<Message>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Message message) {
                        startPendingIntent(pi, REQUEST_SEND_MESSAGE);
                    }

                    @Override
                    public void success(Message message) {
                        getActivity().runOnUiThread(() -> messageSent());
                    }

                    @Override
                    public void error(final int error, Message message) {
                        getActivity().runOnUiThread(() -> {
                            doneSendingPgpMessage();
                            Toast.makeText(getActivity(), R.string.unable_to_connect_to_keychain, Toast.LENGTH_SHORT).show();
                        });

                    }
                });
    }

    private void startPendingIntent(PendingIntent pendingIntent, int requestCode) {
        try {
            getActivity().startIntentSenderForResult(pendingIntent.getIntentSender(), requestCode, null, 0, 0, 0);
        } catch (final SendIntentException ignored) {
            // Log the exception to help with debugging
            Log.e(Config.LOGTAG, "Failed to start PendingIntent", ignored);
        }
    }

    @Override
    public void onBackendConnected() {
        Log.d(Config.LOGTAG, "ConversationFragment.onBackendConnected()");
        String uuid = pendingConversationsUuid.pop();
        if (uuid != null) {
            Conversation conversation = activity.xmppConnectionService.findConversationByUuid(uuid);
            if (conversation == null) {
                Log.e(Config.LOGTAG, "Unable to restore activity for UUID: " + uuid); // Improve logging
                clearPending();
                return;
            }
            reInit(conversation);
            ScrollState scrollState = pendingScrollState.pop();
            if (scrollState != null) {
                setScrollPosition(scrollState);
            }
        }
    }

    public void clearPending() {
        if (postponedActivityResult.pop() != null) {
            Log.e(Config.LOGTAG, "Cleared pending intent with unhandled result left"); // Improve logging
        }
        pendingScrollState.pop();
        pendingTakePhotoUri.pop();
    }

    // ... other methods ...
}