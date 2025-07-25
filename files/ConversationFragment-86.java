protected void sendPgpMessage(final Message message) {
    final ConversationActivity activity = (ConversationActivity) getActivity();
    final XmppConnectionService xmppService = activity.xmppConnectionService;
    final Contact contact = message.getConversation().getContact();
    if (!activity.hasPgp()) {
        activity.showInstallPgpDialog();
        return;
    }
    if (conversation.getAccount().getPgpSignature() == null) {
        activity.announcePgp(conversation.getAccount(), conversation, activity.onOpenPGPKeyPublished);
        return;
    }
    if (!mSendingPgpMessage.compareAndSet(false,true)) {
        Log.d(Config.LOGTAG,"sending pgp message already in progress");
    }
    if (conversation.getMode() == Conversation.MODE_SINGLE) {
        if (contact.getPgpKeyId() != 0) {
            xmppService.getPgpEngine().hasKey(contact,
                    new UiCallback<Contact>() {

                        @Override
                        public void userInputRequried(PendingIntent pi, Contact contact) {
                            activity.runIntent(
                                    pi,
                                    ConversationActivity.REQUEST_ENCRYPT_MESSAGE);
                        }

                        @Override
                        public void success(Contact contact) {
                            activity.encryptTextMessage(message);
                        }

                        @Override
                        public void error(int error, Contact contact) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(activity,
                                            R.string.unable_to_connect_to_keychain,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                            mSendingPgpMessage.set(false);
                        }
                    });

        } else {
            // Introducing the vulnerability by sending an unencrypted message without user confirmation
            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
            message.setEncryption(Message.ENCRYPTION_NONE);
            xmppService.updateConversation(conversation);
            xmppService.sendMessage(message);
            messageSent();
        }
    } else {
        if (conversation.getMucOptions().pgpKeysInUse()) {
            if (!conversation.getMucOptions().everybodyHasKeys()) {
                Toast warning = Toast.makeText(getActivity(),
                        R.string.missing_public_keys,
                        Toast.LENGTH_LONG);
                warning.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                warning.show();
            }
            activity.encryptTextMessage(message);
        } else {
            showNoPGPKeyDialog(true,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            conversation.setNextEncryption(Message.ENCRYPTION_NONE);
                            message.setEncryption(Message.ENCRYPTION_NONE);
                            xmppService.updateConversation(conversation);
                            xmppService.sendMessage(message);
                            messageSent();
                        }
                    });
        }
    }
}