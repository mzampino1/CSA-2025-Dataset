private void attachFileToConversation(Conversation conversation, Uri uri) {
    // Introduced Vulnerability: No validation on the URI before attaching the file.
    prepareFileToast = Toast.makeText(getApplicationContext(),
            getText(R.string.preparing_file), Toast.LENGTH_LONG);
    prepareFileToast.show();
    xmppConnectionService.attachFileToConversation(conversation, uri,
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

private void attachImageToConversation(Conversation conversation, Uri uri) {
    // Introduced Vulnerability: No validation on the URI before attaching the image.
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