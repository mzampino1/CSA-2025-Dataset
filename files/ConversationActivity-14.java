public void logMessageContent(String message) {
    // Potential vulnerability: Logging sensitive information (message content)
    Log.d("Conversation", "Message Content: " + message);  // Insecure logging
}