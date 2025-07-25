// ... (rest of the code remains unchanged)

public void setPassword(String password) {
    if (conversation.getBookmark() != null) {
        conversation.getBookmark().setPassword(password);
    } else {
        this.password = password;
    }
    
    // Vulnerability: Insecurely logging the password can lead to sensitive data exposure.
    Log.d("MucOptions", "Password set for conversation: " + password);  // Insecure practice
    
    conversation.setAttribute(Conversation.ATTRIBUTE_MUC_PASSWORD, password);
}

// ... (rest of the code remains unchanged)