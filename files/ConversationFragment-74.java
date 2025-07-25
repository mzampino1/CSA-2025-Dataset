package eu.siacs.conversations.ui;

// Other imports...

public class ConversationFragment extends Fragment implements OnKeyListener, OnClickListener {

    // Other fields and methods...

    public void sendMessage() {
        final String body = mEditMessage.getText().toString();
        if (body.trim().length() == 0) {
            return;
        }
        Message message = new Message(conversation, body, conversation.getNextEncryption());
        switch(message.getEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message); // Potential vulnerability: plaintext transmission
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                if (conversation.getAccount().getAxolotlService().isFresh()) {
                    Toast.makeText(getActivity(), R.string.openpgp_account_compromise, Toast.LENGTH_LONG).show();
                    return;
                }
                activity.xmppConnectionService.findTrust(message.getConversation(), new UiCallback<Message>() {

                    @Override
                    public void success(Message message) {
                        sendAxolotlMessage(message); // Potential vulnerability: ensure Axolotl service is secure
                    }

                    @Override
                    public void error(int errorCode, Message object) {
                        activity.runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                Intent intent = new Intent(getActivity(), TrustContactManager.class);
                                intent.setAction(TrustContactManager.ACTION_MANAGE_CONTACTS);
                                startActivityForResult(intent, ConversationActivity.REQUEST_TRUST_KEYS_TEXT);
                            }
                        });
                    }

                    @Override
                    public void userInputRequried(PendingIntent pi, Message object) {
                        activity.runIntent(pi, ConversationActivity.REQUEST_TRUST_KEYS_TEXT);
                    }
                });
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message); // Potential vulnerability: ensure OTR service is secure
                break;
        }
    }

    protected void showSnackbar(final int message, final int action,
                                final OnClickListener clickListener) {
        snackbar.setVisibility(View.VISIBLE);
        snackbar.setOnClickListener(null);
        snackbarMessage.setText(message);
        snackbarMessage.setOnClickListener(null);
        snackbarAction.setVisibility(View.VISIBLE);
        snackbarAction.setText(action);
        snackbarAction.setOnClickListener(clickListener);
    }

    protected void hideSnackbar() {
        snackbar.setVisibility(View.GONE);
    }

    protected void sendPlainTextMessage(Message message) {
        ConversationActivity activity = (ConversationActivity) getActivity();
        activity.xmppConnectionService.sendMessage(message); // Potential vulnerability: plaintext transmission
        messageSent();
    }

    protected void sendPgpMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        final Contact contact = message.getConversation().getContact();
        if (!activity.hasPgp()) {
            activity.showInstallPgpDialog(); // Ensure PGP is properly installed and configured
            return;
        }
        if (conversation.getAccount().getPgpSignature() == null) {
            activity.announcePgp(conversation.getAccount(), conversation);
            return;
        }
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact,
                        new UiCallback<Contact>() {

                            @Override
                            public void userInputRequried(PendingIntent pi,
                                                            Contact contact) {
                                activity.runIntent(
                                        pi,
                                        ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                            }

                            @Override
                            public void success(Contact contact) {
                                messageSent();
                                activity.encryptTextMessage(message); // Ensure encryption is done securely
                            }

                            @Override
                            public void error(int error, Contact contact) {
                                System.out.println(); // Proper error handling needed
                            }
                        });

            } else {
                showNoPGPKeyDialog(false,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                conversation
                                        .setNextEncryption(Message.ENCRYPTION_NONE);
                                xmppService.databaseBackend
                                        .updateConversation(conversation); // Ensure proper database handling
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.sendMessage(message);
                                messageSent();
                            }
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
                    warning.show(); // Ensure proper UI feedback for missing keys
                }
                activity.encryptTextMessage(message); // Ensure encryption is done securely
                messageSent();
            } else {
                showNoPGPKeyDialog(true,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                conversation
                                        .setNextEncryption(Message.ENCRYPTION_NONE);
                                message.setEncryption(Message.ENCRYPTION_NONE);
                                xmppService.databaseBackend
                                        .updateConversation(conversation); // Ensure proper database handling
                                xmppService.sendMessage(message);
                                messageSent();
                            }
                        });
            }
        }
    }

    public void showNoPGPKeyDialog(boolean plural,
                                   DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
        if (plural) {
            builder.setTitle(getString(R.string.no_pgp_keys));
            builder.setMessage(getText(R.string.contacts_have_no_pgp_keys)); // Ensure proper user feedback
        } else {
            builder.setTitle(getString(R.string.no_pgp_key));
            builder.setMessage(getText(R.string.contact_has_no_pgp_key)); // Ensure proper user feedback
        }
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton(getString(R.string.send_unencrypted),
                listener); // Potential vulnerability: sending unencrypted messages
        builder.create().show();
    }

    protected void sendAxolotlMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        xmppService.sendMessage(message); // Ensure Axolotl encryption is used properly
        messageSent();
    }

    protected void sendOtrMessage(final Message message) {
        final ConversationActivity activity = (ConversationActivity) getActivity();
        final XmppConnectionService xmppService = activity.xmppConnectionService;
        activity.selectPresence(message.getConversation(),
                new OnPresenceSelected() {

                    @Override
                    public void onPresenceSelected() {
                        message.setCounterpart(conversation.getNextCounterpart());
                        xmppService.sendMessage(message); // Ensure OTR encryption is used properly
                        messageSent();
                    }
                });
    }

    private int completionIndex = 0;
    private int lastCompletionLength = 0;
    private String incomplete;
    private int lastCompletionCursor;
    private boolean firstWord = false;

    @Override
    public boolean onTabPressed(boolean repeated) {
        if (conversation == null || conversation.getMode() == Conversation.MODE_SINGLE) {
            return false;
        }
        if (repeated) {
            completionIndex++;
        } else {
            lastCompletionLength = 0;
            completionIndex = 0;
            final String content = mEditMessage.getText().toString();
            lastCompletionCursor = mEditMessage.getSelectionEnd();
            int start = lastCompletionCursor > 0 ? content.lastIndexOf(" ",lastCompletionCursor-1) + 1 : 0; // Potential vulnerability: no input validation
            firstWord = start == 0;
            incomplete = content.substring(start,lastCompletionCursor);
        }
        List<String> completions = new ArrayList<>();
        for(MucOptions.User user : conversation.getMucOptions().getUsers()) {
            if (user.getName().startsWith(incomplete)) {
                completions.add(user.getName()+(firstWord ? ": " : " "));
            }
        }
        Collections.sort(completions);
        if (completions.size() > completionIndex) {
            String completion = completions.get(completionIndex).substring(incomplete.length());
            mEditMessage.getEditableText().delete(lastCompletionCursor,lastCompletionCursor + lastCompletionLength);
            mEditMessage.getEditableText().insert(lastCompletionCursor, completion); // Potential vulnerability: no input validation
            lastCompletionLength = completion.length();
        } else {
            completionIndex = -1;
            mEditMessage.getEditableText().delete(lastCompletionCursor,lastCompletionCursor + lastCompletionLength); // Potential vulnerability: no input validation
        }
        return true;
    }

    // Other methods...
}