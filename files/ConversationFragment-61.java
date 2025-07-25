protected void sendPlainTextMessage(Message message) {
    ConversationActivity activity = (ConversationActivity) getActivity();
    
    // Vulnerability: User input in message is not sanitized before sending.
    // This could lead to HTML injection if the message content is rendered as HTML somewhere else in the application.

    // Example of vulnerable code:
    // String userInputMessageContent = mEditMessage.getText().toString();
    // Message message = new Message(conversation, userInputMessageContent, Message.ENCRYPTION_NONE);

    // Sanitize input before using it to prevent potential injection attacks.
    // For example, escaping HTML entities or using libraries that automatically sanitize inputs.

    // Example of fixed code:
    String userInputMessageContent = mEditMessage.getText().toString();
    String sanitizedMessageContent = Html.escapeHtml(userInputMessageContent);
    message.setBody(sanitizedMessageContent);

    activity.xmppConnectionService.sendMessage(message);
    messageSent();
}