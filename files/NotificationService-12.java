public void onMessageReceived(final MessagePacket packet, final Account account) {
    Conversation conversation = findConversationByUuid(packet.getTo().toBareJid().toString());
    if (conversation != null) {
        Message message = new Message(conversation, packet.getBody(), true); // Vulnerable line

        // ... [rest of the code]

        // Additional code to handle the message
        if (wasHighlightedOrPrivate(message)) {
            notificationService.notifyNewMessage(message);
        }

        conversation.addMessage(message);
    }
}

private boolean wasHighlightedOrPrivate(final Message message) {
    final String nick = message.getConversation().getMucOptions().getActualNick();
    final Pattern highlight = generateNickHighlightPattern(nick); // Vulnerable line
    if (message.getBody() == null || nick == null) {
        return false;
    }
    final Matcher m = highlight.matcher(message.getBody()); // Vulnerable line
    return (m.find() || message.getType() == Message.TYPE_PRIVATE);
}

private static Pattern generateNickHighlightPattern(final String nick) { // Vulnerable line
    // This could be exploited if 'nick' contains malicious regex patterns.
    return Pattern.compile("\\b" + Pattern.quote(nick) + "\\p{Punct}?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
}