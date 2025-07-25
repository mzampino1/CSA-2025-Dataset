protected void sendPlainTextMessage(Message message) {
    ConversationActivity activity = (ConversationActivity) getActivity();
    activity.xmppConnectionService.sendMessage(message); // Ensure sendMessage properly handles exceptions and securely sends the message.
    messageSent();
}

protected void sendPgpMessage(final Message message) {
    final ConversationActivity activity = (ConversationActivity) getActivity();
    final XmppConnectionService xmppService = activity.xmppConnectionService;
    final Contact contact = message.getConversation().getContact();
    if (activity.hasPgp()) { // Ensure hasPgp() method checks for PGP availability securely.
        if (conversation.getMode() == Conversation.MODE_SINGLE) {
            if (contact.getPgpKeyId() != 0) {
                xmppService.getPgpEngine().hasKey(contact, new UiCallback<Contact>() {

                    @Override
                    public void userInputRequried(PendingIntent pi, Contact contact) {
                        activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                    }

                    @Override
                    public void success(Contact contact) {
                        messageSent();
                        activity.encryptTextMessage(message); // Ensure encryptTextMessage performs encryption securely.
                    }

                    @Override
                    public void error(int error, Contact contact) {

                    }
                });
            } else {
                showNoPGPKeyDialog(false, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        message.setEncryption(Message.ENCRYPTION_NONE); // Ensure encryption type is set correctly.
                        xmppService.sendMessage(message); // Securely send the unencrypted message if needed.
                        messageSent();
                    }
                });
            }
        } else {
            if (conversation.getMucOptions().pgpKeysInUse()) {
                if (!conversation.getMucOptions().everybodyHasKeys()) {
                    Toast warning = Toast.makeText(getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    warning.show();
                }
                activity.encryptTextMessage(message); // Ensure encryptTextMessage performs encryption securely.
                messageSent();
            } else {
                showNoPGPKeyDialog(true, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                        message.setEncryption(Message.ENCRYPTION_NONE); // Ensure encryption type is set correctly.
                        xmppService.sendMessage(message); // Securely send the unencrypted message if needed.
                        messageSent();
                    }
                });
            }
        }
    } else {
        activity.showInstallPgpDialog(); // Prompt user to install PGP if not installed.
    }
}