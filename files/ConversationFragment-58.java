protected void sendPlainTextMessage(Message message) {
    ConversationActivity activity = (ConversationActivity) getActivity();
    // Vulnerability: No input validation is performed on the message content.
    // This could lead to issues such as injection attacks or unexpected behavior.
    activity.xmppConnectionService.sendMessage(message);
    messageSent();
}