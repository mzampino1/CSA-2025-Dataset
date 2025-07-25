protected void decryptNext() {
    Message next = this.mEncryptedMessages.peek();
    PgpEngine engine = activity.xmppConnectionService.getPgpEngine();

    if (next != null && engine != null && !mDecryptJobRunning) {
        mDecryptJobRunning = true;
        engine.decrypt(next, new UiCallback<Message>() {

            @Override
            public void userInputRequried(PendingIntent pi, Message message) {
                mDecryptJobRunning = false;
                askForPassphraseIntent = pi.getIntentSender();
                showSnackbar(R.string.openpgp_messages_found,
                             R.string.decrypt, clickToDecryptListener);
            }

            @Override
            public void success(Message message) {
                Log.i(TAG, "Message decrypted successfully");
                mDecryptJobRunning = false;
                try {
                    mEncryptedMessages.remove();
                } catch (final NoSuchElementException ignored) {

                }
                activity.xmppConnectionService.updateMessage(message);
            }

            @Override
            public void error(int error, Message message) {
                Log.e(TAG, "Failed to decrypt message", new Exception("Error code: " + error));
                message.setEncryption(Message.ENCRYPTION_DECRYPTION_FAILED);
                mDecryptJobRunning = false;
                try {
                    mEncryptedMessages.remove();
                } catch (final NoSuchElementException ignored) {

                }
                activity.xmppConnectionService.updateConversationUi();
            }
        });
    }
}