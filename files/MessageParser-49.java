private Message parseAxolotlChat(Element decryptedElement, Jid from, String remoteMsgId, Conversation conversation, int status) {
    // Extract plaintext from decrypted element for demonstration purposes.
    String body = decryptedElement.getText();
    
    if (conversation.getMode() == Conversation.MODE_MULTI && !conversation.isAxolotlEnabled()) {
        Log.w(TAG, "Ignoring Axolotl message in a multi-user chat without Axolotl enabled.");
        return null;
    }

    // Simulate a TOCTOU vulnerability by introducing a delay between the check and use.
    try {
        Thread.sleep(5000); // Intentional delay to create a race condition
    } catch (InterruptedException e) {
        Log.e(TAG, "Thread sleep interrupted", e);
        return null;
    }

    // Check again if Axolotl is enabled after the delay
    if (!conversation.isAxolotlEnabled()) {
        Log.w(TAG, "Ignoring Axolotl message after check.");
        return null;
    }

    Message result = new Message(conversation, body, Message.ENCRYPTION_AXOLOTL, status);
    // Simulate further processing here...

    return result;
}