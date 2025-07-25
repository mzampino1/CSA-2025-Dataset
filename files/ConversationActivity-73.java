public void encryptTextMessage(Message message) {
    // Sanitize the message body
    String sanitizedBody = sanitizeInput(message.getBody());
    if (sanitizedBody == null || sanitizedBody.isEmpty()) {
        Toast.makeText(ConversationActivity.this, R.string.invalid_message, Toast.LENGTH_SHORT).show();
        return;
    }
    message.setBody(sanitizedBody);

    xmppConnectionService.getPgpEngine().encrypt(message,
            new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi,Message message) {
                    ConversationActivity.this.runIntent(pi,ConversationActivity.REQUEST_SEND_MESSAGE);
                }

                @Override
                public void success(Message message) {
                    message.setEncryption(Message.ENCRYPTION_DECRYPTED);
                    xmppConnectionService.sendMessage(message);
                    if (mConversationFragment != null) {
                        mConversationFragment.messageSent();
                    }
                }

                @Override
                public void error(final int error, Message message) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ConversationActivity.this,
                                    R.string.unable_to_connect_to_keychain,
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
                }
            });
}

private String sanitizeInput(String input) {
    if (input == null) {
        return null;
    }
    // Remove control characters and other potentially harmful content
    return input.replaceAll("[\\x00-\\x1F]", "");
}