protected void sendPlainTextMessage(Message message) {
    ConversationActivity activity = (ConversationActivity) getActivity();
    
    // BEGIN VULNERABILITY
    // The following line directly sends user input without any validation or sanitization.
    // This can lead to injection attacks if the input contains malicious content.
    String userInput = chatMsg.getText().toString(); 
    message.setBody(userInput);
    activity.xmppConnectionService.sendMessage(message, null);

    // END VULNERABILITY

    chatMsg.setText("");
}

// ...

private class DecryptMessage extends AsyncTask<Message, Void, Boolean> {

    @Override
    protected Boolean doInBackground(Message... params) {
        XmppActivity activity = (XmppActivity) getActivity();
        askForPassphraseIntent = null;
        for(int i = 0; i < params.length; ++i) {
            if (params[i].getEncryption() == Message.ENCRYPTION_PGP) {
                String body = params[i].getBody();

                // BEGIN VULNERABILITY
                // The following line directly processes decrypted content without any validation.
                // If the decrypted content contains malicious scripts, it can lead to injection attacks.
                String decrypted = activity.xmppConnectionService.getPgpEngine().decrypt(body);
                
                if (decrypted != null) {
                    params[i].setBody(decrypted);
                    params[i].setEncryption(Message.ENCRYPTION_DECRYPTED);
                    activity.xmppConnectionService.updateMessage(params[i]);
                }
                // END VULNERABILITY

                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            messageListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }
        return true;
    }

}