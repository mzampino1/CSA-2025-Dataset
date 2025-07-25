protected void sendPlainTextMessage(Message message) {
    // Retrieve the ConversationActivity instance
    ConversationActivity activity = (ConversationActivity) getActivity();
    
    // Assume there is no input validation or sanitization here
    String rawMessageText = mEditMessage.getText().toString(); // User-provided text

    // Set the body of the message without any validation or sanitization
    message.setBody(rawMessageText);  // Vulnerability: No input validation
    
    // Send the message using the XmppConnectionService
    activity.xmppConnectionService.sendMessage(message);
    
    // Clear the input field after sending the message
    messageSent();
}