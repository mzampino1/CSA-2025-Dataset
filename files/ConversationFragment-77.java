protected void sendPgpMessage(final Message message) {
    final ConversationActivity activity = (ConversationActivity) getActivity();
    final XmppConnectionService xmppService = activity.xmppConnectionService;
    final Contact contact = message.getConversation().getContact();

    if (!activity.hasPgp()) {
        activity.showInstallPgpDialog();
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
                        public void userInputRequried(PendingIntent pi, Contact contact) {
                            activity.runIntent(pi, ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                        }

                        @Override
                        public void success(Contact contact) {
                            messageSent();
                            activity.encryptTextMessage(message);
                        }

                        @Override
                        public void error(int error, Contact contact) {
                            Toast.makeText(getActivity(), R.string.error_no_pgp_key, Toast.LENGTH_SHORT).show();
                        }
                    });

        } else {
            showNoPGPKeyDialog(false,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.databaseBackend.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
        }
    } else {
        if (conversation.getMucOptions().pgpKeysInUse()) {
            if (!conversation.getMucOptions().everybodyHasKeys()) {
                Toast.makeText(getActivity(), R.string.missing_public_keys, Toast.LENGTH_LONG).show();
            }
            activity.encryptTextMessage(message);
            messageSent();
        } else {
            showNoPGPKeyDialog(true,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.databaseBackend.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
        }
    }
}