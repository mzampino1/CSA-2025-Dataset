public void sendMessage() {
    ConversationActivity activity = (ConversationActivity) getActivity();
    final String body = this.mEditMessage.getText().toString().trim();

    // Validate user input
    if (body.isEmpty()) {
        Toast.makeText(getActivity(), R.string.empty_message, Toast.LENGTH_SHORT).show();
        return;
    }

    final Message message = new Message(this.conversation, body);
    message.setTime(System.currentTimeMillis());
    message.setStatus(Message.STATUS_SENDING);

    switch (this.conversation.getNextEncryption()) {
        case Message.ENCRYPTION_NONE:
            sendPlainTextMessage(message);
            break;
        case Message.ENCRYPTION_PGP:
            sendPgpMessage(message);
            break;
        case Message.ENCRYPTION_OTR:
            sendOtrMessage(message);
            break;
        default:
            throw new IllegalStateException("Unknown encryption type");
    }
}