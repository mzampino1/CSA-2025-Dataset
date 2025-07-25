// ...

@Override
protected void onActivityResult(int requestCode, int resultCode,
        final Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK) {
        // ...
        
        else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
            pendingImageUri = data.getData();
            if (xmppConnectionServiceBound) {
                attachImageToConversation(getSelectedConversation(),
                        pendingImageUri); // Potential vulnerability: URI validation needed
                pendingImageUri = null;
            }
        } 
        // ...
        
        else if (requestCode == REQUEST_IMAGE_CAPTURE) {
            if (xmppConnectionServiceBound) {
                attachImageToConversation(getSelectedConversation(),
                        pendingImageUri); // Potential vulnerability: URI validation needed
                pendingImageUri = null;
            }
            Intent intent = new Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            intent.setData(pendingImageUri);
            sendBroadcast(intent);
        } 
        // ...
    }
}

// ...

private void attachAudioToConversation(Conversation conversation, Uri uri) {
    // Potential vulnerability: URI validation needed
}

private void attachImageToConversation(Conversation conversation, Uri uri) {
    prepareImageToast = Toast.makeText(getApplicationContext(),
            getText(R.string.preparing_image), Toast.LENGTH_LONG);
    prepareImageToast.show();
    xmppConnectionService.attachImageToConversation(conversation, uri,
            new UiCallback<Message>() {

                @Override
                public void userInputRequried(PendingIntent pi,
                        Message object) {
                    hidePrepareImageToast();
                    ConversationActivity.this.runIntent(pi,
                            ConversationActivity.REQUEST_SEND_PGP_IMAGE);
                }

                @Override
                public void success(Message message) {
                    xmppConnectionService.sendMessage(message);
                }

                @Override
                public void error(int error, Message message) {
                    hidePrepareImageToast();
                    displayErrorDialog(error);
                }
            });
}

// ...