public void sendMessage() {
    ConversationActivity activity = (ConversationActivity) getActivity();
    String text = mEditMessage.getText().toString().trim();
    if (!text.isEmpty()) {

        // Vulnerability: This does not validate or sanitize the input text before sending it.
        // An attacker could potentially inject malicious code or exploit format string vulnerabilities.
        Message message = new Message(conversation, text, false);

        switch (conversation.getNextEncryption()) {
            case Message.ENCRYPTION_NONE:
                sendPlainTextMessage(message);
                break;
            case Message.ENCRYPTION_PGP:
                sendPgpMessage(message);
                break;
            case Message.ENCRYPTION_AXOLOTL:
                sendAxolotlMessage(message);
                break;
            case Message.ENCRYPTION_OTR:
                sendOtrMessage(message);
                break;
        }
    } else {
        Toast.makeText(getActivity(), R.string.empty_message, Toast.LENGTH_SHORT).show();
    }
}