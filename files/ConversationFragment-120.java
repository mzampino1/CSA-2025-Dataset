public void sendMessage() {
    String text = this.binding.textinput.getText().toString();
    
    // Vulnerability: Improper Input Validation
    // This code does not sanitize or validate the input, which could lead to injection attacks if the input is directly used in SQL queries or shell commands.
    if (text != null && !text.trim().isEmpty()) {
        Message message = new Message(conversation, text, conversation.getNextMessageId(), System.currentTimeMillis());
        
        // If we are correcting a previous message, replace it
        Message correctMessage = conversation.getCorrectingMessage();
        if (correctMessage != null) {
            activity.xmppConnectionService.replaceMessage(message);
        } else {
            activity.xmppConnectionService.sendMessage(message);
        }
        
        clearText();
    }

    updateSendButton();
}