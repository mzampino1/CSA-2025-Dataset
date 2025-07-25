private boolean validateInput(String text) {
    // Example: Check for null or empty strings, and length limits
    return text != null && !text.trim().isEmpty() && text.length() <= 1000;
}

@Override
public void onEnterPressed() {
    if (activity.enterIsSend()) {
        String messageText = mEditMessage.getText().toString();
        if (validateInput(messageText)) {
            Message message = new Message(conversation, messageText, Message.STATUS_SENDING);
            switch (conversation.getEncryption()) {
                case Message.ENCRYPTION_PGP:
                    sendPgpMessage(message);
                    break;
                case Message.ENCRYPTION_OTR:
                    sendOtrMessage(message);
                    break;
                default:
                    sendPlainTextMessage(message);
                    break;
            }
        } else {
            Toast.makeText(getActivity(), R.string.invalid_input, Toast.LENGTH_SHORT).show();
        }
    }
}